<?php
/**
 * Includes information for how to index MARC Records.  Allows for the ability to handle multiple data sources.
 *
 * @category Pika
 * @author Mark Noble <mark@marmot.org>
 * Date: 6/30/2015
 * Time: 1:44 PM
 */

require_once ROOT_DIR . '/sys/Indexing/TranslationMap.php';
class IndexingProfile extends DB_DataObject{
	public $__table = 'indexing_profiles';    // table name

	public $id;
	public $name;
	public $marcPath;
	public $marcEncoding;
	public $individualMarcPath;
	public $groupingClass;
	public $indexingClass;
	public $recordDriver;
	public $recordUrlComponent;
	public $formatSource;
	public $recordNumberTag;
	public $recordNumberPrefix;
	public $suppressItemlessBibs;
	public $itemTag;
	public $itemRecordNumber;
	public $useItemBasedCallNumbers;
	public $callNumberPrestamp;
	public $callNumber;
	public $callNumberCutter;
	public $callNumberPoststamp;
	public $location;
	public $locationsToSuppress;
	public $subLocation;
	public $shelvingLocation;
	public $collection;
	public $volume;
	public $itemUrl;
	public $barcode;
	public $status;
	public $statusesToSuppress;
	public $totalCheckouts;
	public $lastYearCheckouts;
	public $yearToDateCheckouts;
	public $totalRenewals;
	public $iType;
	public $dueDate;
	public $dateCreated;
	public $dateCreatedFormat;
	public $iCode2;
	public $useICode2Suppression;
	public $format;
	public $eContentDescriptor;
	public $orderTag;
	public $orderStatus;
	public $orderLocation;
	public $orderCopies;
	public $orderCode3;

	function getObjectStructure(){
		$translationMapStructure = TranslationMap::getObjectStructure();
		unset($translationMapStructure['indexingProfileId']);

		$structure = array(
			'id' => array('property'=>'id', 'type'=>'label', 'label'=>'Id', 'description'=>'The unique id within the database'),
			'name' => array('property' => 'name', 'type' => 'text', 'label' => 'Name', 'maxLength' => 50, 'description' => 'A name for this indexing profile', 'required' => true),
			'marcPath' => array('property' => 'marcPath', 'type' => 'text', 'label' => 'MARC Path', 'maxLength' => 100, 'description' => 'The path on the server where MARC records can be found', 'required' => true),
			'marcEncoding' => array('property' => 'marcEncoding', 'type' => 'enum', 'label' => 'MARC Encoding', 'values' => array('MARC8' => 'MARC8', 'UTF8' => 'UTF8', 'UNIMARC' => 'UNIMARC', 'ISO8859_1' => 'ISO8859_1', 'BESTGUESS' => 'BESTGUESS'), 'default' => 'MARC8'),
			'individualMarcPath' => array('property' => 'individualMarcPath', 'type' => 'text', 'label' => 'Individual MARC Path', 'maxLength' => 100, 'description' => 'The path on the server where individual MARC records can be found', 'required' => true),
			'groupingClass' => array('property' => 'groupingClass', 'type' => 'text', 'label' => 'Grouping Class', 'maxLength' => 50, 'description' => 'The class to use while grouping the records', 'required' => true, 'default' => 'MarcRecordGrouper'),
			'indexingClass' => array('property' => 'indexingClass', 'type' => 'text', 'label' => 'Indexing Class', 'maxLength' => 50, 'description' => 'The class to use while indexing the records', 'required' => true, 'default' => 'IlsRecord'),
			'recordDriver' => array('property' => 'recordDriver', 'type' => 'text', 'label' => 'Record Driver', 'maxLength' => 50, 'description' => 'The record driver to use while displaying information in Pika', 'required' => true, 'default' => 'MarcRecord'),
			'recordUrlComponent' => array('property' => 'recordUrlComponent', 'type' => 'text', 'label' => 'Record URL Component', 'maxLength' => 50, 'description' => 'The Module to use within the URL', 'required' => true, 'default' => 'Record'),
			'formatSource' => array('property' => 'formatSource', 'type' => 'enum', 'label' => 'Load Format from', 'values' => array('bib' => 'Bib Record', 'item' => 'Item Record'), 'default' => 'bib'),

			'recordNumberTag' => array('property' => 'recordNumberTag', 'type' => 'text', 'label' => 'Record Number Tag', 'maxLength' => 3, 'description' => 'The MARC tag where the record number can be found', 'required' => true),
			'recordNumberPrefix' => array('property' => 'recordNumberPrefix', 'type' => 'text', 'label' => 'Record Number Prefix', 'maxLength' => 10, 'description' => 'A prefix to identify the bib record number if multiple MARC tags exist'),
			'suppressItemlessBibs' => array('property' => 'itemTag', 'type' => 'checkbox', 'label' => 'Suppress Itemless Bibs', 'description' => 'Whether or not Itemless Bibs can be suppressed'),

			'itemTag' => array('property' => 'itemTag', 'type' => 'text', 'label' => 'Item Tag', 'maxLength' => 3, 'description' => 'The MARC tag where items can be found'),
			'itemRecordNumber' => array('property' => 'itemRecordNumber', 'type' => 'text', 'label' => 'Item Record Number', 'maxLength' => 1, 'description' => 'Subfield for the record number for the item'),
			'useItemBasedCallNumbers' => array('property' => 'useItemBasedCallNumbers', 'type' => 'checkbox', 'label' => 'Use Item Based Call Numbers', 'description' => 'Whether or not we should use call number information from the bib or from the item records'),
			'callNumberPrestamp' => array('property' => 'callNumberPrestamp', 'type' => 'text', 'label' => 'Call Number Prestamp', 'maxLength' => 1, 'description' => 'Subfield for call number pre-stamp'),
			'callNumber' => array('property' => 'callNumber', 'type' => 'text', 'label' => 'Call Number', 'maxLength' => 1, 'description' => 'Subfield for call number'),
			'callNumberCutter' => array('property' => 'callNumberCutter', 'type' => 'text', 'label' => 'Call Number Cutter', 'maxLength' => 1, 'description' => 'Subfield for call number cutter'),
			'callNumberPoststamp' => array('property' => 'callNumberPoststamp', 'type' => 'text', 'label' => 'Call Number Poststamp', 'maxLength' => 1, 'description' => 'Subfield for call number pre-stamp'),
			'location' => array('property' => 'location', 'type' => 'text', 'label' => 'Location', 'maxLength' => 1, 'description' => 'Subfield for location'),
			'locationsToSuppress' => array('property' => 'locationsToSuppress', 'type' => 'text', 'label' => 'Locations To Suppress', 'maxLength' => 100, 'description' => 'A regular expression for any locations that should be suppressed'),
			'subLocation' => array('property' => 'subLocation', 'type' => 'text', 'label' => 'Sub Location', 'maxLength' => 1, 'description' => 'A secondary subfield to divide locations'),
			'shelvingLocation' => array('property' => 'shelvingLocation', 'type' => 'text', 'label' => 'Shelving Location', 'maxLength' => 1, 'description' => 'A subfield for shelving location information'),
			'collection' => array('property' => 'collection', 'type' => 'text', 'label' => 'Collection', 'maxLength' => 1, 'description' => 'A subfield for collection information'),
			'volume' => array('property' => 'volume', 'type' => 'text', 'label' => 'Volume', 'maxLength' => 1, 'description' => 'A subfield for volume information'),
			'itemUrl' => array('property' => 'itemUrl', 'type' => 'text', 'label' => 'Item URL', 'maxLength' => 1, 'description' => 'Subfield for a URL specific to the item'),
			'barcode' => array('property' => 'barcode', 'type' => 'text', 'label' => 'Barcode', 'maxLength' => 1, 'description' => 'Subfield for barcode'),
			'status' => array('property' => 'status', 'type' => 'text', 'label' => 'Status', 'maxLength' => 1, 'description' => 'Subfield for status'),
			'statusesToSuppress' => array('property' => 'statusesToSuppress', 'type' => 'text', 'label' => 'Statuses To Suppress', 'maxLength' => 100, 'description' => 'A regular expression for any statuses that should be suppressed'),
			'totalCheckouts' => array('property' => 'totalCheckouts', 'type' => 'text', 'label' => 'Total Checkouts', 'maxLength' => 1, 'description' => 'Subfield for total checkouts'),
			'lastYearCheckouts' => array('property' => 'lastYearCheckouts', 'type' => 'text', 'label' => 'Last Year Checkouts', 'maxLength' => 1, 'description' => 'Subfield for checkouts done last year'),
			'yearToDateCheckouts' => array('property' => 'yearToDateCheckouts', 'type' => 'text', 'label' => 'Year To Date', 'maxLength' => 1, 'description' => 'Subfield for checkouts so far this year'),
			'totalRenewals' => array('property' => 'totalRenewals', 'type' => 'text', 'label' => 'Total Renewals', 'maxLength' => 1, 'description' => 'Subfield for number of times this record has been renewed'),
			'iType' => array('property' => 'iType', 'type' => 'text', 'label' => 'iType', 'maxLength' => 1, 'description' => 'Subfield for iType'),
			'dueDate' => array('property' => 'dueDate', 'type' => 'text', 'label' => 'Due Date', 'maxLength' => 1, 'description' => 'Subfield for when the item is due'),
			'dateCreated' => array('property' => 'dateCreated', 'type' => 'text', 'label' => 'Date Created', 'maxLength' => 1, 'description' => 'Subfield for when the item was created'),
			'dateCreatedFormat' => array('property' => 'dateCreatedFormat', 'type' => 'text', 'label' => 'Date Created Format', 'maxLength' => 20, 'description' => 'The format of the date created.  I.e. yyMMdd see SimpleDateFormat for Java'),
			'iCode2' => array('property' => 'iCode2', 'type' => 'text', 'label' => 'iCode2', 'maxLength' => 1, 'description' => 'Subfield for icode2'),
			'useICode2Suppression' => array('property' => 'useICode2Suppression', 'type' => 'checkbox', 'label' => 'Use iCode2 suppression for items', 'description' => 'Whether or not we should suppress items based on iCode2'),
			'format' => array('property' => 'format', 'type' => 'text', 'label' => 'Format', 'maxLength' => 1, 'description' => 'The subfield to use when determining format based on item information'),
			'eContentDescriptor' => array('property' => 'eContentDescriptor', 'type' => 'text', 'label' => 'eContent Descriptor', 'maxLength' => 1, 'description' => 'Subfield to indicate that the item should be processed as eContent and how to process it'),

			'orderTag' => array('property' => 'orderTag', 'type' => 'text', 'label' => 'Order Tag', 'maxLength' => 3, 'description' => 'The MARC tag where order records can be found'),
			'orderStatus' => array('property' => 'orderStatus', 'type' => 'text', 'label' => 'Order Status', 'maxLength' => 1, 'description' => 'Subfield for status of the order item'),
			'orderLocation' => array('property' => 'orderLocation', 'type' => 'text', 'label' => 'Order Location', 'maxLength' => 1, 'description' => 'Subfield for location of the order item'),
			'orderCopies' => array('property' => 'orderCopies', 'type' => 'text', 'label' => 'Order Copies', 'maxLength' => 1, 'description' => 'The number of copies if not shown within location'),
			'orderCode3' => array('property' => 'orderCode3', 'type' => 'text', 'label' => 'Order Code3', 'maxLength' => 1, 'description' => 'Code 3 for the order record'),

			'translationMaps' => array(
				'property' => 'translationMaps',
				'type'=> 'oneToMany',
				'label' => 'Translation Maps',
				'description' => 'The translation maps for the profile.',
				'keyThis' => 'id',
				'keyOther' => 'indexingProfileId',
				'subObjectType' => 'TranslationMap',
				'structure' => $translationMapStructure,
				'sortable' => false,
				'storeDb' => true,
				'allowEdit' => true,
				'canEdit' => true,
			),

		);
		return $structure;
	}

	public function __get($name){
		if ($name == "translationMaps") {
			if (!isset($this->translationMaps)){
				//Get the list of translation maps
				$this->translationMaps = array();
				$translationMap = new TranslationMap();
				$translationMap->indexingProfileId = $this->id;
				$translationMap->orderBy('name ASC');
				$translationMap->find();
				while($translationMap->fetch()){
					$this->translationMaps[$translationMap->id] = clone($translationMap);
				}
			}
			return $this->translationMaps;
		}else if ($name == "locationMaps") {
			if (!isset($this->locationMaps)){
				//Get the list of location maps
				$this->locationMaps = array();
				$translationMap = new LocationMap();
				$translationMap->indexingProfileId = $this->id;
				$translationMap->find();
				while($translationMap->fetch()){
					$this->locationMaps[$translationMap->id] = clone($translationMap);
				}
			}
			return $this->locationMaps;
		}
		return null;
	}

	public function __set($name, $value){
		if ($name == "translationMaps") {
			$this->translationMaps = $value;
		}elseif ($name == "locationMaps") {
			$this->locationMaps = $value;
		}
	}

	/**
	 * Override the update functionality to save the associated translation maps
	 *
	 * @see DB/DB_DataObject::update()
	 */
	public function update(){
		$ret = parent::update();
		if ($ret === FALSE ){
			return $ret;
		}else{
			$this->saveTranslationMaps();
			$this->saveLocationMaps();
		}
		/** @var Memcache $memCache */
		global $memCache;
		global $serverName;
		$memCache->delete("{$serverName}_indexing_profiles");
		return true;
	}

	/**
	 * Override the update functionality to save the associated translation maps
	 *
	 * @see DB/DB_DataObject::insert()
	 */
	public function insert(){
		$ret = parent::insert();
		if ($ret === FALSE ){
			return $ret;
		}else{
			$this->saveTranslationMaps();
			$this->saveLocationMaps();
		}
		/** @var Memcache $memCache */
		global $memCache;
		global $serverName;
		$memCache->delete("{$serverName}_indexing_profiles");
		return true;
	}

	public function saveTranslationMaps(){
		if (isset ($this->translationMaps)){
			foreach ($this->translationMaps as $translationMap){
				if (isset($translationMap->deleteOnSave) && $translationMap->deleteOnSave == true){
					$translationMap->delete();
				}else{
					if (isset($translationMap->id) && is_numeric($translationMap->id)){
						$translationMap->update();
					}else{
						$translationMap->indexingProfileId = $this->id;
						$translationMap->insert();
					}
				}
			}
			//Clear the translation maps so they are reloaded the next time
			unset($this->translationMaps);
		}
	}

	public function saveLocationMaps(){
		if (isset ($this->locationMaps)){
			foreach ($this->locationMaps as $locationMap){
				if (isset($locationMap->deleteOnSave) && $locationMap->deleteOnSave == true){
					$locationMap->delete();
				}else{
					if (isset($locationMap->id) && is_numeric($locationMap->id)){
						$locationMap->update();
					}else{
						$locationMap->indexingProfileId = $this->id;
						$locationMap->insert();
					}
				}
			}
			//Clear the location maps so they are reloaded the next time
			unset($this->locationMaps);
		}
	}
}