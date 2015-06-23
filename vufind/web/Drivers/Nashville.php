<?php
/**
 *
 * Copyright (C) Villanova University 2007.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
require_once ROOT_DIR . '/Drivers/Millennium.php';
/**
 * VuFind Connector for Nashville Public Library's Innovative catalog (Millenium)
 *
 * This class uses screen scraping techniques to gather record holdings written
 * by Adam Bryn of the Tri-College consortium.
 *
 * @author Adam Brin <abrin@brynmawr.com>
 *
 * Extended by Mark Noble and CJ O'Hara based on specific requirements for
 * Marmot Library Network.
 *
 * @author Mark Noble <mnoble@turningleaftech.com>
 * @author CJ O'Hara <cj@marmot.org>
 * 
 * Extended by James Staub based on specific requirements for
 * Nashville Public Library
 *
 * @author James Staub <james.staub@nashville.gov>
 */
class Nashville extends MillenniumDriver{
	public function __construct(){
		$this->fixShortBarcodes = false;
	}

	/**
	 * Initialize and configure curl connection
	 */
	public function _curl_connect($curl_url){
		$header = array();
		$header[0] = "Accept: text/xml,application/xml,application/xhtml+xml,";
		$header[0] .= "text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5";
		$header[] = "Cache-Control: max-age=0";
		$header[] = "Connection: keep-alive";
		$header[] = "Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7";
		$header[] = "Accept-Language: en-us,en;q=0.5";
		$cookie_jar = tempnam ("/tmp", "CURLCOOKIE");
		$curl_connection = curl_init();
		curl_setopt($curl_connection, CURLOPT_CONNECTTIMEOUT, 30);
		curl_setopt($curl_connection, CURLOPT_COOKIEJAR, $cookie_jar);
		curl_setopt($curl_connection, CURLOPT_COOKIESESSION, true); // JAMES 20150617: ?
		curl_setopt($curl_connection, CURLOPT_FOLLOWLOCATION, true);
		curl_setopt($curl_connection, CURLOPT_FORBID_REUSE, false);
		curl_setopt($curl_connection, CURLOPT_HEADER, false); // should set CURLOPT_HEADER to false[?] in production - JAMES 20140830
		curl_setopt($curl_connection, CURLOPT_HTTPHEADER, $header);
		curl_setopt($curl_connection, CURLOPT_RETURNTRANSFER, true); // should set CURLOPT_RETURNTRANSFER to true in production - JAMES 20140830
		curl_setopt($curl_connection, CURLOPT_SSL_VERIFYPEER, true); // should set CURLOPT_SSL_VERIFYPEER to true in production - JAMES 20140830
		curl_setopt($curl_connection, CURLOPT_UNRESTRICTED_AUTH, true);
		curl_setopt($curl_connection, CURLOPT_USERAGENT,"Pika 2015.10.0");
		curl_setopt($curl_connection, CURLOPT_URL, $curl_url);

		curl_setopt($curl_connection, CURLOPT_HTTPGET, true);

		return($curl_connection);
	}                                        

	/**
	 * Login with barcode and pin
	 *
	 * @see Drivers/Millennium::patronLogin()
	 */
	public function patronLogin($barcode, $pin)
	{
		global $configArray;
		global $timer;
		global $logger;
		if ($configArray['Catalog']['offline'] == true){
			//$logger->log("Trying to authenticate in offline mode $barcode, $pin", PEAR_LOG_DEBUG);
			//The catalog is offline, check the database to see if the user is valid
			$user = new User();
			$user->cat_username = $barcode;
			$user->cat_password = $pin;
			if ($user->find(true)){
				//$logger->log("Found the user", PEAR_LOG_DEBUG);
				$returnVal = array(
					'id'        => $user->id,
					'username'  => $user->username,
					'firstname' => $user->firstname,
					'lastname'  => $user->lastname,
					'fullname'  => $user->firstname . ' ' . $user->lastname,     //Added to array for possible display later.
					'cat_username' => $barcode, //Should this be $Fullname or $patronDump['PATRN_NAME']
					'cat_password' => $pin,
					'email' => $user->email,
					'major' => null,
					'college' => null,
					'patronType' => $user->patronType,
					'web_note' => translate('The catalog is currently down.  You will have limited access to circulation information.'));
				$timer->logTime("patron logged in successfully");
				return $returnVal;
			} else {
				//$logger->log("Did not find a user for that barcode and pin", PEAR_LOG_DEBUG);
				$timer->logTime("patron login failed");
				return null;
			}
		}else{
			// if patron attempts to Create New PIN
			if (isset($_REQUEST['password2']) && strlen($_REQUEST['password2']) > 0){
				$this->_pin_create($barcode,$_REQUEST['password'],$_REQUEST['password2']);
			}
			// check barcode/pin credentials
			$userValid = $this->_pin_test($barcode, $pin);
			if ($userValid){
				//Load the raw information about the patron
				$patronDump = $this->_getPatronDump($barcode, true);
				$Fullname = $patronDump['PATRN_NAME']; // James Staub chose this simpler route over some $Fullname replace acrobatics in Millennium.php 20131205
				$nameParts = explode(',',$Fullname);
				$lastname = strtolower($nameParts[0]);
				$middlename = isset($nameParts[2]) ? strtolower($nameParts[2]) : ''; 
				$firstname = isset($nameParts[1]) ? strtolower($nameParts[1]) : $middlename;
				$user = array(
					'id'		=> $barcode,
					'username'	=> $patronDump['RECORD_#'],
					'firstname'	=> $firstname,
					'lastname'	=> $lastname,
					'fullname'	=> $Fullname,	//Added to array for possible display later.
					'cat_username'	=> $barcode,	//Should this be $Fullname or $patronDump['PATRN_NAME']
		                	'cat_password'	=> $pin,
	                		'email'		=> isset($patronDump['EMAIL_ADDR']) ? $patronDump['EMAIL_ADDR'] : '',
	                		'major'		=> null,
	                		'college'	=> null,
					'patronType'	=> $patronDump['P_TYPE'],
					'web_note'	=> isset($patronDump['WEB_NOTE']) ? $patronDump['WEB_NOTE'] : '');
					$timer->logTime("patron logged in successfully");
					return $user;
			} else {
				$timer->logTime("patron login failed");
				return null;
			}
		}
	}

	public function _getLoginFormValues(){
		global $user;
		$loginData = array();
		$loginData['pin'] = $user->cat_password;
		$loginData['code'] = $user->cat_username;
		$loginData['submit'] = 'submit';
		return $loginData;
	}

	public function _getBarcode(){
		global $user;
		return $user->cat_username;
	}

	protected function _getHoldResult($holdResultPage){
		$hold_result = array();
		//Get rid of header and footer information and just get the main content
		$matches = array();
		if (preg_match('/success/', $holdResultPage)){
			//Hold was successful
			$hold_result['result'] = true;
			if (!isset($reason) || strlen($reason) == 0){
				$hold_result['message'] = 'Your hold was placed successfully';
			}else{
				$hold_result['message'] = $reason;
			}
		}else if (preg_match('/<font color="red" size="\+2">(.*?)<\/font>/is', $holdResultPage, $reason)){
			//Got an error message back.
			$hold_result['result'] = false;
			$hold_result['message'] = $reason[1];
		}else{
			//Didn't get a reason back.  This really shouldn't happen.
			$hold_result['result'] = false;
			$hold_result['message'] = 'Did not receive a response from the circulation system.  Please try again in a few minutes.';
		}
		return $hold_result;
	}

	protected function _pin_test($barcode, $pin) {
		global $configArray;
		$pin = urlencode($pin);
		$apiurl = $configArray['OPAC']['patron_host'] . "/PATRONAPI/$barcode/$pin/pintest";
		$curl_connection = $this->_curl_connect($apiurl);
		$api_contents = curl_exec($curl_connection);
		curl_close($curl_connection);
		$api_contents = trim(strip_tags($api_contents));
		//$logger->log('PATRONAPI pintest response : ' . $api_contents, PEAR_LOG_DEBUG);
		$api_array_lines = explode("\n", $api_contents);
		foreach ($api_array_lines as $api_line) {
			$api_line_arr = explode("=", $api_line);
			$api_data[trim($api_line_arr[0])] = trim($api_line_arr[1]);
		}
		if (!isset($api_data['RETCOD'])){
			$userValid = false;
		}else if ($api_data['RETCOD'] == 0){
			$userValid = true;
		}else{
			$userValid = false;
		}
		return $userValid;
	}

	protected function _pin_create($barcode, $pin1, $pin2) {
		global $configArray;
// Case: Millennium patron record does not have PIN. 
// When a library uses IPSSO - Innovative Patron Single Sign On - the initial login does a redirect and requires additonal parameters.
// ipsso.html ifpinneeded token is active. Need to do an actual login rather than just checking patron dump
// 1. retrieve the ipsso.html page at /patroninfo to scrape the 'lt' value
// 2. POST the first round, pin=''
// 3. retrieve the ipsso.html page primed to receive pin1 and pin2 input, scrape the second 'lt' value
// 4. POST the second round, pin1 and pin2 from user input
// 5. display ipsso-generated error messages
// 6. PATRONAPI dump
// 7. PATRONAPI pintest
// 8. display PATRONAPI error messages
// 9. redirect patron to /MyAccount/Home
		$curl_url = $configArray['Catalog']['url'] . "/patroninfo";
		$curl_connection = $this->_curl_connect($curl_url);
		$sresult = curl_exec($curl_connection);
		//Scrape the 'lt' value from the IPSSO login page
		if (preg_match('/<input type="hidden" name="lt" value="(.*?)" \/>/si', $sresult, $loginMatches)) {
					$lt = $loginMatches[1];
				//POST the first round - pin is blank
				        $post_data['code'] = $barcode;
				        $post_data['pin'] = "";
				        $post_data['lt'] = $lt;
				        $post_data['_eventId'] = 'submit';
				        $post_items = array();
				        foreach ($post_data as $key => $value) {
				                $post_items[] = $key . '=' . $value;
				        }
				        $post_string = implode ('&', $post_items);
					$redirectPageInfo = curl_getinfo($curl_connection, CURLINFO_EFFECTIVE_URL);
				        curl_setopt($curl_connection, CURLOPT_URL, $redirectPageInfo);
					curl_setopt($curl_connection, CURLOPT_POST, true);
				        curl_setopt($curl_connection, CURLOPT_POSTFIELDS, $post_string);
				        $sresult = curl_exec($curl_connection);
				//Is the patron's PIN already set?
					if (preg_match('/<fieldset class="newpin" id="ipssonewpin">/si', $sresult, $newPinMatches)) {
					//if (preg_match('/Please enter a new PIN/si', $sresult, $newPinMatches)) {
				//Scrape the 'lt' value from the IPSSO login page primed to receive a new PIN, which is different from the last page's 'lt' value
						if (preg_match('/<input type="hidden" name="lt" value="(.*?)" \/>/si', $sresult, $loginMatches)) {
							$lt2 = $loginMatches[1];
				//POST the second round - pin1 and pin2
							$post_data['code'] = $barcode;
							$post_data['pin1'] = $pin1;
							$post_data['pin2'] = $pin2;
							$post_data['lt'] = $lt2;
							$post_data['_eventId'] = 'submit';
							$post_items = array();
							foreach ($post_data as $key => $value) {
								$post_items[] = $key . '=' . $value;
							}
							$post_string = implode ('&', $post_items);
							$redirectPageInfo = curl_getinfo($curl_connection, CURLINFO_EFFECTIVE_URL);
							curl_setopt($curl_connection, CURLOPT_URL, $redirectPageInfo);
							curl_setopt($curl_connection, CURLOPT_POST, true);
							curl_setopt($curl_connection, CURLOPT_POSTFIELDS, $post_string);
							$sresult = curl_exec($curl_connection);
							if (preg_match('/<div id="status" class="errors">(.+?)<\/div>/si', $sresult, $ipssoErrors)) {
								$ipssoError = $ipssoErrors[1];
								//echo($ipssoError."\n");
//SOME ERROR MESSAGES THAT APPEAR WHEN SETTING PIN
//PIN insertion failed. Your record is in use by system. Please try again later.
//Please enter a new PIN.
//PINs do not match. Try again!
//Your pin is not complex enough to be secure. Please select another one.
//Marmot[?] error catchers
//if (preg_match('/the information you submitted was invalid/i', $sresult)){
//	PEAR_Singleton::raiseError('Unable to register your new pin #.  The pin was invalid or this account already has a pin set for it.');
//}else if (preg_match('/PIN insertion failed/i', $sresult)){
//	PEAR_Singleton::raiseError('Unable to register your new pin #.  PIN insertion failed.');
//}
							}
						} else {
							//echo("lt2 not found at " . $redirectPageInfo . "\n");
						}
					} else {
						//PIN is already set in patron record
						//echo("new PIN message NOT FOUND at " . $redirectPageInfo . "\n");
					}
				} else {
					//echo("lt not found in sresult\n");
				}
				//unlink($cookie_jar); // 20150617 JAMES commented out while messing around - need to ensure user1 doesn't accidentally get user2 info
	}

	protected function updatePin(){
		global $user;
		global $configArray;
		if (!$user){
			return "You must be logged in to update your pin number.";
		}
		if (isset($_REQUEST['pin'])){
			$pin = $_REQUEST['pin'];
		}else{
			return "Please enter your current pin number";
		}
		if ($user->cat_password != $pin){
			return "The current pin number is incorrect";
		}
		if (isset($_REQUEST['pin1'])){
			$pin1 = $_REQUEST['pin1'];
		}else{
			return "Please enter the new pin number";
		}
		if (isset($_REQUEST['pin2'])){
			$pin2 = $_REQUEST['pin2'];
		}else{
			return "Please enter the new pin number again";
		}
		if ($pin1 != $pin2){
			return "The pin numberdoes not match the confirmed number, please try again.";
		}
		//Login to the patron's account
		$cookieJar = tempnam ("/tmp", "CURLCOOKIE");
		$success = false;
		$barcode = $this->_getBarcode();
		$patronDump = $this->_getPatronDump($barcode);
		//Login to the site
		$curl_url = $configArray['Catalog']['url'] . "/patroninfo";
		$curl_connection = curl_init($curl_url);
		$header=array();
		$header[0] = "Accept: text/xml,application/xml,application/xhtml+xml,";
		$header[0] .= "text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5";
		$header[] = "Cache-Control: max-age=0";
		$header[] = "Connection: keep-alive";
		$header[] = "Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7";
		$header[] = "Accept-Language: en-us,en;q=0.5";
		curl_setopt($curl_connection, CURLOPT_CONNECTTIMEOUT, 30);
		curl_setopt($curl_connection, CURLOPT_HTTPHEADER, $header);
		curl_setopt($curl_connection, CURLOPT_USERAGENT,"Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)");
		curl_setopt($curl_connection, CURLOPT_RETURNTRANSFER, true);
		curl_setopt($curl_connection, CURLOPT_SSL_VERIFYPEER, false);
		curl_setopt($curl_connection, CURLOPT_FOLLOWLOCATION, 1);
		curl_setopt($curl_connection, CURLOPT_UNRESTRICTED_AUTH, true);
		curl_setopt($curl_connection, CURLOPT_COOKIEJAR, $cookieJar );
		curl_setopt($curl_connection, CURLOPT_COOKIESESSION, false);
		curl_setopt($curl_connection, CURLOPT_POST, true);
		$post_data = $this->_getLoginFormValues($patronDump);
		foreach ($post_data as $key => $value) {
			$post_items[] = $key . '=' . urlencode($value);
		}
		$post_string = implode ('&', $post_items);
		curl_setopt($curl_connection, CURLOPT_POSTFIELDS, $post_string);
		$sresult = curl_exec($curl_connection);
		//Issue a post request to update the pin
		$post_data = array();
		$post_data['pin']= $pin;
		$post_data['pin1']= $pin1;
		$post_data['pin2']= $pin2;
		$post_data['submit.x']="35";
		$post_data['submit.y']="15";
		$post_items = array();
		foreach ($post_data as $key => $value) {
			$post_items[] = $key . '=' . urlencode($value);
		}
		$post_string = implode ('&', $post_items);
		curl_setopt($curl_connection, CURLOPT_POSTFIELDS, $post_string);
		$curl_url = $configArray['Catalog']['url'] . "/patroninfo/" .$patronDump['RECORD_#'] . "/newpin";
		curl_setopt($curl_connection, CURLOPT_URL, $curl_url);
		$sresult = curl_exec($curl_connection);
		curl_close($curl_connection);
		unlink($cookieJar);
		if ($sresult){
			if (preg_match('/<FONT COLOR=RED SIZE= 2><EM>(.*?)</EM></FONT>/i', $sresult, $matches)){
				return $matches[1];
			}else{
				$user->cat_password = $pin1;
				$user->update();
				UserAccount::updateSession($user);
				return "Your pin number was updated sucessfully.";
			}
		}else{
			return "Sorry, we could not update your pin number. Please try again later.";
		}
	}
	function selfRegister(){
		global $logger;
		global $configArray;
		$firstName = $_REQUEST['firstName'];
		$middleInitial = $_REQUEST['middleInitial'];
		$lastName = $_REQUEST['lastName'];
		$address1 = $_REQUEST['address1'];
		$address2 = $_REQUEST['address2'];
		$address3 = $_REQUEST['address3'];
		$address4 = $_REQUEST['address4'];
		$email = $_REQUEST['email'];
		$gender = $_REQUEST['gender'];
		$birthDate = $_REQUEST['birthDate'];
		$phone = $_REQUEST['phone'];
		$cookie = tempnam ("/tmp", "CURLCOOKIE");
		$curl_url = $configArray['Catalog']['url'] . "/selfreg~S" . $this->getMillenniumScope();
		$logger->log('Loading page ' . $curl_url, PEAR_LOG_INFO);
		//echo "$curl_url";
		$curl_connection = curl_init($curl_url);
		curl_setopt($curl_connection, CURLOPT_CONNECTTIMEOUT, 30);
		curl_setopt($curl_connection, CURLOPT_USERAGENT,"Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)");
		curl_setopt($curl_connection, CURLOPT_RETURNTRANSFER, true);
		curl_setopt($curl_connection, CURLOPT_SSL_VERIFYPEER, false);
		curl_setopt($curl_connection, CURLOPT_FOLLOWLOCATION, 1);
		curl_setopt($curl_connection, CURLOPT_UNRESTRICTED_AUTH, true);
		$post_data['nfirst'] = $firstName;
		$post_data['nmiddle'] = $middleInitial;
		$post_data['nlast'] = $lastName;
		$post_data['stre_aaddress'] = $address1;
		$post_data['city_aaddress'] = $address2;
		$post_data['stre_haddress2'] = $address3;
		$post_data['city_haddress2'] = $address4;
		$post_data['zemailaddr'] = $email;
		$post_data['F045pcode2'] = $gender;
		$post_data['F051birthdate'] = $birthDate;
		$post_data['tphone1'] = $phone;
		foreach ($post_data as $key => $value) {
			$post_items[] = $key . '=' . urlencode($value);
		}
		$post_string = implode ('&', $post_items);
		curl_setopt($curl_connection, CURLOPT_POSTFIELDS, $post_string);
		$sresult = curl_exec($curl_connection);
		curl_close($curl_connection);
		//Parse the library card number from the response
		if (preg_match('/Your temporary library card number is :.*?(\\d+)<\/(b|strong|span)>/si', $sresult, $matches)) {
			$barcode = $matches[1];
			return array('success' => true, 'barcode' => $barcode);
		} else {
			global $logger;
			$logger->log("$sresult", PEAR_LOG_DEBUG);
			return array('success' => false, 'barcode' => null);
		}
	}
}
