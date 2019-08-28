<?php
/**
 *  Basic Trait to use with class AJAX Handler for operations that are used with MARC records.
 *
 * @category Pika
 * @author   : Pascal Brammeier
 * Date: 4/28/2019
 *
 */

require_once ROOT_DIR . '/AJAXHandler.php';

trait MARC_AJAX_Basic {

	function __construct(){

		// Add allowed AJAX method calls to the ones already set
		$this->methodsThatRespondWithJSONUnstructured  = array_merge($this->methodsThatRespondWithJSONUnstructured, array(
			'reloadCover',
		));
		$this->methodsThatRespondWithJSONResultWrapper = array_merge($this->methodsThatRespondWithJSONResultWrapper, array());
		$this->methodsThatRespondWithHTML             = array_merge($this->methodsThatRespondWithHTML, array());
		$this->methodsThatRespondWithXML              = array_merge($this->methodsThatRespondWithXML, array());
		$this->methodsThatRespondThemselves           = array_merge($this->methodsThatRespondThemselves, array(
			'downloadMarc',
		));

	}

	function downloadMarc(){
		require_once ROOT_DIR . '/services/SourceAndId.php';
		require_once ROOT_DIR . '/RecordDrivers/MarcRecord.php';
		$sourceAndId      = new SourceAndId($_REQUEST['id']);
		$marcData         = MarcLoader::loadMarcRecordByILSId($sourceAndId);
		$downloadFileName = urlencode($sourceAndId);
		header('Content-Description: File Transfer');
		header('Content-Type: application/octet-stream');
		header("Content-Disposition: attachment; filename*={$downloadFileName}.mrc");
		header('Content-Transfer-Encoding: binary');
		header('Expires: 0');
		header('Cache-Control: must-revalidate');
		header('Pragma: public');
		header('Content-Length: ' . strlen($marcData->toRaw()));
		ob_clean();
		flush();
		echo($marcData->toRaw());
	}

	function reloadCover(){
		require_once ROOT_DIR . '/services/SourceAndId.php';
		require_once ROOT_DIR . '/RecordDrivers/MarcRecord.php';
		$sourceAndId  = new SourceAndId($_REQUEST['id']);
		$recordDriver = RecordDriverFactory::initRecordDriverById($sourceAndId);

		//Reload small cover
		$smallCoverUrl = str_replace('&amp;', '&', $recordDriver->getBookcoverUrl('small')) . '&reload';
		file_get_contents($smallCoverUrl);

		//Reload medium cover
		$mediumCoverUrl = str_replace('&amp;', '&', $recordDriver->getBookcoverUrl('medium')) . '&reload';
		file_get_contents($mediumCoverUrl);

		//Reload large cover
		$largeCoverUrl = str_replace('&amp;', '&', $recordDriver->getBookcoverUrl('large')) . '&reload';
		file_get_contents($largeCoverUrl);

		//Also reload covers for the grouped work
		require_once ROOT_DIR . '/RecordDrivers/GroupedWorkDriver.php';
		$groupedWorkDriver = new GroupedWorkDriver($recordDriver->getGroupedWorkId());

		//Reload small cover
		$smallCoverUrl = str_replace('&amp;', '&', $groupedWorkDriver->getBookcoverUrl('small', true)) . '&reload';
		file_get_contents($smallCoverUrl);

		//Reload medium cover
		$mediumCoverUrl = str_replace('&amp;', '&', $groupedWorkDriver->getBookcoverUrl('medium', true)) . '&reload';
		file_get_contents($mediumCoverUrl);

		//Reload large cover
		$largeCoverUrl = str_replace('&amp;', '&', $groupedWorkDriver->getBookcoverUrl('large', true)) . '&reload';
		file_get_contents($largeCoverUrl);

		return array('success' => true, 'message' => 'Covers have been reloaded.  You may need to refresh the page to clear your local cache.');
	}

}