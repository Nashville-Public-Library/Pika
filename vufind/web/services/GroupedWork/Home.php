<?php
/**
 * Description goes here
 *
 * @category Pika
 * @author Mark Noble <mark@marmot.org>
 * Date: 11/27/13
 * Time: 12:14 PM
 */
require_once ROOT_DIR  . '/Action.php';
class GroupedWork_Home extends Action{
	function launch() {
		global $interface;
		global $timer;
		global $logger;

		$id = $_REQUEST['id'];

		require_once ROOT_DIR . '/RecordDrivers/GroupedWorkDriver.php';
		$recordDriver = new GroupedWorkDriver($id);
		if (!$recordDriver->isValid){
			$logger->log("Did not find a record for id {$id} in solr." , PEAR_LOG_DEBUG);
//			$interface->setTemplate('../Record/invalidRecord.tpl');
//			$interface->display('layout.tpl');
			$this->display('../Record/invalidRecord.tpl', 'Error');
			die();
		}
		$interface->assign('recordDriver', $recordDriver);
		$timer->logTime('Initialized the Record Driver');

		// Retrieve User Search History
		$interface->assign('lastsearch', isset($_SESSION['lastSearchURL']) ? $_SESSION['lastSearchURL'] : false);

		//Get Next/Previous Links
		$searchSource = isset($_REQUEST['searchSource']) ? $_REQUEST['searchSource'] : 'local';
		/** @var SearchObject_Solr $searchObject */
		$searchObject = SearchObjectFactory::initSearchObject();
		$searchObject->init($searchSource);
		$searchObject->getNextPrevLinks();

		$interface->assign('moreDetailsOptions', $recordDriver->getMoreDetailsOptions());


		$interface->assign('metadataTemplate', 'GroupedWork/metadata.tpl');

		$interface->assign('semanticData', json_encode($recordDriver->getSemanticData()));

		// Display Page
//		$interface->setPageTitle($recordDriver->getTitle());
//		$interface->setTemplate('full-record.tpl');
//		$interface->assign('sidebar', 'GroupedWork/full-record-sidebar.tpl');
//		$interface->assign('moreDetailsTemplate', 'GroupedWork/moredetails-accordion.tpl');
//		$interface->display('layout.tpl');

		$this->display('full-record.tpl', $recordDriver->getTitle());
	}

	/**
	 * @param string $mainContentTemplate  Name of the SMARTY template file for the main content of the Grouped Work Page
	 * @param string $pageTitle     What to display is the html title tag
	 * @param bool|true $sidebar    enables the account sidebar on the page to be displayed
	 */
//	function display($mainContentTemplate, $pageTitle='Grouped Work', $sidebar=true) {
//		global $interface;
////		if ($sidebar) $interface->assign('sidebar', 'GroupedWork/full-record-sidebar.tpl');
//		if ($sidebar) $interface->assign('sidebar', 'Search/home-sidebar.tpl');
////		TODO: is this the best template to use?
//		$interface->setTemplate($mainContentTemplate);
//		$interface->setPageTitle($pageTitle);
//		$interface->display('layout.tpl');
//	}
}