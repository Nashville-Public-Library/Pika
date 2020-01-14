<?php


// This deletes XMNPS from Carl database.

// TO DO: set up github repository

// deletePatronNote is handled by Carl!
// deletePatronUserDefinedFields is handled by Carl! NB standard and urgent notes will be deleted

// echo 'SYNTAX: path/to/php NashvilleCarlXDeletePatrons.php, e.g., $ sudo /opt/rh/php55/root/usr/bin/php NashvilleCarlXDeletePatrons.php\n';

date_default_timezone_set('America/Chicago');

$configArray		= parse_ini_file('/usr/local/vufind-plus/sites/nashville.test/conf/config.pwd.ini', true, INI_SCANNER_RAW);
$carlx_db_php		= $configArray['Catalog']['carlx_db_php'];
$carlx_db_php_user	= $configArray['Catalog']['carlx_db_php_user'];
$carlx_db_php_password	= $configArray['Catalog']['carlx_db_php_password'];
$patronApiWsdl		= $configArray['Catalog']['patronApiWsdl'];
$reportPath		= '../data/';

function callAPI($wsdl, $requestName, $request) {
	$connectionPassed = false;
	$numTries = 0;
	$result = new stdClass();
	$result->response = "";
	while (!$connectionPassed && $numTries < 3) {
		try {
			$client = new SOAPClient($wsdl, array('connection_timeout' => 3, 'features' => SOAP_WAIT_ONE_WAY_CALLS, 'trace' => 1));
			$result->response = $client->$requestName($request);
			$connectionPassed = true;
			$result->response = $client->__getLastResponse();
			if (!empty($result->response)) {
				$result->success = stripos($result->response, '<ns2:ShortMessage>Successful operation</ns2:ShortMessage>') !== false;
				if(!$result->success) {
					preg_match('/<ns2:LongMessage>(.+?)<\/ns2:LongMessage>/', $result->response, $longMessages);
					$result->error = "$request->SearchID : Failed" . (isset($longMessages[1]) ? ' : ' . $longMessages[1] : '');
				}
			} else {
				$result->error = "$request->SearchID : Failed : No SOAP response from API.";
			}
		} catch (SoapFault $e) {
			if ($numTries == 2) { $result->error = "$request->SearchID : Exception : " . $e->getMessage(); }
		}
		$numTries++;
	}
	return $result;
}

// connect to carlx oracle db
$conn = oci_connect($carlx_db_php_user, $carlx_db_php_password, $carlx_db_php);
if (!$conn) {
	$e = oci_error();
	trigger_error(htmlentities($e['message'], ENT_QUOTES), E_USER_ERROR);
}

$sql = <<<EOT
--DELETING XMNPS staff- JBL 
select patronid
from patron_v
where bty = 38
and jts.todate(expdate) < (sysdate -14)
and name not like 'TEST %'
and name not like '% TEST %'
and patronid not like '190%'
and patronid not in (select tr.patronid from transitem_v tr)
 
EOT;

$stid = oci_parse($conn, $sql);
oci_set_prefetch($stid, 10000);
oci_execute($stid);
// start a new file for the CarlX patron extract
$df;
$df = fopen($reportPath . "CARLX_MNPS_DELETE_PATRONS.CSV", 'w');
        
while (($row = oci_fetch_array ($stid, OCI_ASSOC+OCI_RETURN_NULLS)) != false) {
	// CSV OUTPUT
	fputcsv($df, $row);
}
fclose($df);
echo "CARLX MNPS patrons to be deleted retrieved and written\n";
oci_free_statement($stid);
oci_close($conn);

$records = array();
$fhnd = fopen($reportPath . "CARLX_MNPS_DELETE_PATRONS.CSV", "r");
if ($fhnd){
        while (($data = fgetcsv($fhnd)) !== FALSE){
                $records[] = $data;
        }
}

$i = 0;
$errors = array();
foreach ($records as $patron) {
	// CREATE PATRON DELETE REQUEST
	$requestName							= 'deletePatron';
	$request							= new stdClass();
	$request->Modifiers						= new stdClass();
	$request->Modifiers->DebugMode					= true;
	$request->Modifiers->ReportMode					= false;
	$request->SearchType						= 'Patron ID';
	$request->SearchID						= $patron[0]; // Patron ID
	//var_dump($request);
	$result = callAPI($patronApiWsdl, $requestName, $request);
	//var_dump($result);
	if (isset($result->error)) {
		echo "$result->error\n";
		$errors[] = $result->error;
	} else {
		echo "$request->SearchID : deleted\n";
	}
	//if(++$i==100) break;
}

$ferror = fopen($reportPath . "NashvilleCarlXDeletePatrons.log", "a");
fwrite($ferror, "-------------------------------------------------------------\n");
fwrite($ferror, date('c') . " BEGIN DELETE PATRONS\n");
fwrite($ferror, $sql . "\n");
fwrite($ferror, implode(',',array_column($records,0)) . "\n");
fwrite($ferror, implode("\n",$errors) . "\n\n");
fclose($ferror);

?>
