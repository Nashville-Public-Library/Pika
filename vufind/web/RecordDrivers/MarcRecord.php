<?php
/**
 *
 * Copyright (C) Villanova University 2010.
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
require_once 'File/MARC.php';

require_once ROOT_DIR . '/RecordDrivers/IndexRecord.php';

/**
 * MARC Record Driver
 *
 * This class is designed to handle MARC records.  Much of its functionality
 * is inherited from the default index-based driver.
 */
class MarcRecord extends IndexRecord
{
	/** @var File_MARC_Record $marcRecord */
	protected $marcRecord = null;

	protected $profileType;
	protected $id;
	/** @var  IndexingProfile $indexingProfile */
	protected $indexingProfile;
	protected $valid = null;

	/**
	 * Constructor.  We build the object using all the data retrieved
	 * from the (Solr) index.  Since we have to
	 * make a search call to find out which record driver to construct,
	 * we will already have this data available, so we might as well
	 * just pass it into the constructor.
	 *
	 * @param   array|File_MARC_Record||string   $recordData     Data to construct the driver from
	 * @access  public
	 */
	public function __construct($recordData){
		if ($recordData instanceof File_MARC_Record){
			$this->marcRecord = $recordData;
		}elseif (is_string($recordData)){
			require_once ROOT_DIR . '/sys/MarcLoader.php';
			if (strpos($recordData, ':') !== false){
				$recordInfo = explode(':', $recordData);
				$this->profileType = $recordInfo[0];
				$this->id = $recordInfo[1];
			}else{
				$this->profileType = 'ils';
				$this->id = $recordData;
			}

			global $indexingProfiles;
			if (array_key_exists($this->profileType, $indexingProfiles)){
				$this->indexingProfile = $indexingProfiles[$this->profileType];
			}else{
				//Try to infer the indexing profile from the module
				global $activeRecordProfile;
				if ($activeRecordProfile){
					$this->indexingProfile = $activeRecordProfile;
				}else{
					$this->indexingProfile = $indexingProfiles['ils'];
				}
			}
		}else{
			// Call the parent's constructor...
			parent::__construct($recordData);

			// Also process the MARC record:
			require_once ROOT_DIR . '/sys/MarcLoader.php';
			$this->marcRecord = MarcLoader::loadMarcRecordFromRecord($recordData);
			if (!$this->marcRecord) {
				$this->valid = false;
			}
		}
		if (!isset($this->id) && $this->valid){
			/** @var File_MARC_Data_Field $idField */
			global $configArray;
			$idField = $this->marcRecord->getField($configArray['Reindex']['recordNumberTag']);
			if ($idField){
				$this->id = $idField->getSubfield('a')->getData();
			}
		}
		global $timer;
		$timer->logTime("Base initialization of MarcRecord Driver");
		parent::loadGroupedWork();
	}

	public function getModule(){
		return isset($this->indexingProfile) ? $this->indexingProfile->recordUrlComponent : 'Record';
	}

	protected $itemsFromIndex;
	public function setItemsFromIndex($itemsFromIndex, $realTimeStatusNeeded){
		global $configArray;
		if ($configArray['Catalog']['supportsRealtimeIndexing'] || !$realTimeStatusNeeded || $configArray['Catalog']['offline']){
			$this->itemsFromIndex = $itemsFromIndex;
		}
	}

	protected $detailedRecordInfoFromIndex;
	public function setDetailedRecordInfoFromIndex($detailedRecordInfoFromIndex, $realTimeStatusNeeded){
		global $configArray;
		if ($configArray['Catalog']['supportsRealtimeIndexing'] || !$realTimeStatusNeeded || $configArray['Catalog']['offline']){
			$this->detailedRecordInfoFromIndex = $detailedRecordInfoFromIndex;
		}
	}

	public function isValid(){
		if ($this->valid == null){
			$this->valid = MarcLoader::marcExistsForILSId($this->getIdWithSource());
		}
		return $this->valid;
	}

	/**
	 * Return the unique identifier of this record within the Solr index;
	 * useful for retrieving additional information (like tags and user
	 * comments) from the external MySQL database.
	 *
	 * @access  public
	 * @return  string              Unique identifier.
	 */
	public function getUniqueID()
	{
		if (isset($this->id)){
			return $this->id;
		}else{
			return $this->fields['id'];
		}
	}

	/**
	 * Return the unique identifier of this record within the Solr index;
	 * useful for retrieving additional information (like tags and user
	 * comments) from the external MySQL database.
	 *
	 * @access  public
	 * @return  string              Unique identifier.
	 */
	public function getId()
	{
		if (isset($this->id)){
			return $this->id;
		}else{
			return $this->fields['id'];
		}
	}

	public function getIdWithSource(){
		return $this->profileType . ':' . $this->id;
	}

	/**
	 * Return the unique identifier of this record within the Solr index;
	 * useful for retrieving additional information (like tags and user
	 * comments) from the external MySQL database.
	 *
	 * @access  public
	 * @return  string              Unique identifier.
	 */
	public function getShortId()
	{
		$shortId = '';
		if (isset($this->id)){
			$shortId = $this->id;
			if (strpos($shortId, '.b') === 0){
				$shortId = str_replace('.b', 'b', $shortId);
				$shortId = substr($shortId, 0, strlen($shortId) -1);
			}
		}
		return $shortId;
	}

	/**
	 * Assign necessary Smarty variables and return a template name to
	 * load in order to export the record in the requested format.  For
	 * legal values, see getExportFormats().  Returns null if format is
	 * not supported.
	 *
	 * @param   string  $format     Export format to display.
	 * @access  public
	 * @return  string              Name of Smarty template file to display.
	 */
	public function getExport($format)
	{
		global $interface;

		switch(strtolower($format)) {
			case 'endnote':
				// This makes use of core metadata fields in addition to the
				// assignment below:
				header('Content-type: application/x-endnote-refer');
				$interface->assign('marc', $this->getMarcRecord());
				return 'RecordDrivers/Marc/export-endnote.tpl';
			case 'marc':
				$interface->assign('rawMarc', $this->getMarcRecord()->toRaw());
				return 'RecordDrivers/Marc/export-marc.tpl';
			case 'rdf':
				header("Content-type: application/rdf+xml");
				$interface->assign('rdf', $this->getRDFXML());
				return 'RecordDrivers/Marc/export-rdf.tpl';
			case 'refworks':
				// To export to RefWorks, we actually have to redirect to
				// another page.  We'll do that here when the user requests a
				// RefWorks export, then we'll call back to this module from
				// inside RefWorks using the "refworks_data" special export format
				// to get the actual data.
				$this->redirectToRefWorks();
				break;
			case 'refworks_data':
				// This makes use of core metadata fields in addition to the
				// assignment below:
				header('Content-type: text/plain');
				$interface->assign('marc', $this->getMarcRecord());
				return 'RecordDrivers/Marc/export-refworks.tpl';
			default:
				return null;
		}
		return null;
	}

	/**
	 * Get an array of strings representing formats in which this record's
	 * data may be exported (empty if none).  Legal values: "RefWorks",
	 * "EndNote", "MARC", "RDF".
	 *
	 * @access  public
	 * @return  array               Strings representing export formats.
	 */
	public function getExportFormats()
	{
		// Get an array of legal export formats (from config array, or use defaults
		// if nothing in config array).
		global $configArray;
		global $library;
		$active = isset($configArray['Export']) ?
		$configArray['Export'] : array('RefWorks' => true, 'EndNote' => true);

		// These are the formats we can possibly support if they are turned on in
		// config.ini:
		$possible = array('RefWorks', 'EndNote', 'MARC', 'RDF');

		// Check which formats are currently active:
		$formats = array();
		foreach($possible as $current) {
			if ($active[$current]) {
				if (!isset($library) || (strlen($library->exportOptions) > 0 &&  preg_match('/' . $library->exportOptions . '/i', $current))){
					//the library didn't filter out the export method
					$formats[] = $current;
				}
			}
		}

		// Send back the results:
		return $formats;
	}

	/**
	 * Get an XML RDF representation of the data in this record.
	 *
	 * @access  public
	 * @return  mixed               XML RDF data (false if unsupported or error).
	 */
	public function getRDFXML()
	{
		// Get Record as MARCXML
		$xml = trim($this->getMarcRecord()->toXML());

		// Load Stylesheet
		$style = new DOMDocument;
		$style->load('services/Record/xsl/record-rdf-mods.xsl');

		// Setup XSLT
		$xsl = new XSLTProcessor();
		$xsl->importStyleSheet($style);

		// Transform MARCXML
		$doc = new DOMDocument;
		if ($doc->loadXML($xml)) {
			return $xsl->transformToXML($doc);
		}

		// If we got this far, something went wrong.
		return false;
	}

	/**
	 * Assign necessary Smarty variables and return a template name for the current
	 * view to load in order to display a summary of the item suitable for use in
	 * search results.
	 *
	 * @param string $view The current view.
	 * @param boolean $useUnscopedHoldingsSummary Whether or not the $result should show an unscoped holdings summary.
	 *
	 * @return string      Name of Smarty template file to display.
	 * @access public
	 */
	public function getSearchResult($view = 'list', $useUnscopedHoldingsSummary = false)
	{
		global $interface;

		// MARC results work just like index results, except that we want to
		// enable the AJAX status display since we assume that MARC records
		// come from the ILS:
		$template = parent::getSearchResult($view, $useUnscopedHoldingsSummary);
		$interface->assign('summAjaxStatus', true);
		return $template;
	}

	/**
	 * Assign necessary Smarty variables and return a template name to
	 * load in order to display the full record information on the Staff
	 * View tab of the record view page.
	 *
	 * @access  public
	 * @return  string              Name of Smarty template file to display.
	 */
	public function getStaffView()
	{
		global $interface;

		$interface->assign('marcRecord', $this->getMarcRecord());

		$solrRecord = $this->fields;
		if ($solrRecord){
			ksort($solrRecord);
		}
		$interface->assign('solrRecord', $solrRecord);
		return 'RecordDrivers/Marc/staff.tpl';
	}

	/**
	 * Assign necessary Smarty variables and return a template name to
	 * load in order to display the Table of Contents extracted from the
	 * record.  Returns null if no Table of Contents is available.
	 *
	 * @access  public
	 * @return  string              Name of Smarty template file to display.
	 */
	public function getTOC()
	{
		$tableOfContents = array();
		$marcFields505 = $this->getMarcRecord()->getFields('505');
		if ($marcFields505){
			$tableOfContents = $this->processTableOfContentsFields($marcFields505);
		}
		return $tableOfContents;
	}

	/**
	 * Does this record have a Table of Contents available?
	 *
	 * @access  public
	 * @return  bool
	 */
	public function hasTOC()
	{
		// Is there a table of contents in the MARC record?
		if ($this->getMarcRecord()->getFields('505')) {
			return true;
		}
		return false;
	}

	/**
	 * Does this record support an RDF representation?
	 *
	 * @access  public
	 * @return  bool
	 */
	public function hasRDF()
	{
		return true;
	}

	/**
	 * Get access restriction notes for the record.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getAccessRestrictions()
	{
		return $this->getFieldArray('506');
	}

	/**
	 * Get all subject headings associated with this record.  Each heading is
	 * returned as an array of chunks, increasing from least specific to most
	 * specific.
	 *
	 * @access  protected
	 * @return array
	 */
	protected function getAllSubjectHeadings()
	{
		// These are the fields that may contain subject headings:
		$fields = array('600', '610', '630', '650', '651', '655');

		// This is all the collected data:
		$retval = array();

		// Try each MARC field one at a time:
		foreach($fields as $field) {
			// Do we have any results for the current field?  If not, try the next.
			/** @var File_MARC_Data_Field[] $results */
			$results = $this->getMarcRecord()->getFields($field);
			if (!$results) {
				continue;
			}

			// If we got here, we found results -- let's loop through them.
			foreach($results as $result) {
				// Start an array for holding the chunks of the current heading:
				$current = array();

				// Get all the chunks and collect them together:
				/** @var File_MARC_Subfield[] $subfields */
				$subfields = $result->getSubfields();
				if ($subfields) {
					foreach($subfields as $subfield) {
						//Add unless this is 655 subfield 2
						if ($subfield->getCode() == 2){
							//Suppress this code
						}else{
							$current[] = $subfield->getData();
						}
					}
					// If we found at least one chunk, add a heading to our $result:
					if (!empty($current)) {
						$retval[] = $current;
					}
				}
			}
		}

		// Send back everything we collected:
		return $retval;
	}

	/**
	 * Get award notes for the record.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getAwards()
	{
		return $this->getFieldArray('586');
	}

	/**
	 * Get notes on bibliography content.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getBibliographyNotes()
	{
		return $this->getFieldArray('504');
	}

	/**
	 * Get the main corporate author (if any) for the record.
	 *
	 * @access  protected
	 * @return  string
	 */
	protected function getCorporateAuthor()
	{
		return $this->getFirstFieldValue('110', array('a', 'b'));
	}

	/**
	 * Return an array of all values extracted from the specified field/subfield
	 * combination.  If multiple subfields are specified and $concat is true, they
	 * will be concatenated together in the order listed -- each entry in the array
	 * will correspond with a single MARC field.  If $concat is false, the return
	 * array will contain separate entries for separate subfields.
	 *
	 * @param   string      $field          The MARC field number to read
	 * @param   array       $subfields      The MARC subfield codes to read
	 * @param   bool        $concat         Should we concatenate subfields?
	 * @access  private
	 * @return  array
	 */
	private function getFieldArray($field, $subfields = null, $concat = true)
	{
		// Default to subfield a if nothing is specified.
		if (!is_array($subfields)) {
			$subfields = array('a');
		}

		// Initialize return array
		$matches = array();

		// Try to look up the specified field, return empty array if it doesn't exist.
		$fields = $this->getMarcRecord()->getFields($field);
		if (!is_array($fields)) {
			return $matches;
		}

		// Extract all the requested subfields, if applicable.
		foreach($fields as $currentField) {
			$next = $this->getSubfieldArray($currentField, $subfields, $concat);
			$matches = array_merge($matches, $next);
		}

		return $matches;
	}

	/**
	 * Get the edition of the current record.
	 *
	 * @access  public
	 * @param   boolean $returnFirst whether or not only the first value is desired
	 * @return  string
	 */
	public function getEdition($returnFirst = false)
	{
		if ($returnFirst){
			return $this->getFirstFieldValue('250');
		}else{
			return $this->getFieldArray('250');
		}

	}

	/**
	 * Get notes on finding aids related to the record.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getFindingAids()
	{
		return $this->getFieldArray('555');
	}

	/**
	 * Get the first value matching the specified MARC field and subfields.
	 * If multiple subfields are specified, they will be concatenated together.
	 *
	 * @param   string      $field          The MARC field to read
	 * @param   array       $subfields      The MARC subfield codes to read
	 * @access  private
	 * @return  string
	 */
	private function getFirstFieldValue($field, $subfields = null)
	{
		$matches = $this->getFieldArray($field, $subfields);
		return (is_array($matches) && count($matches) > 0) ?
		$matches[0] : null;
	}

	/**
	 * Get general notes on the record.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getGeneralNotes()
	{
		return $this->getFieldArray('500');
	}

	/**
	 * Get the item's places of publication.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getPlacesOfPublication()
	{
		$placesOfPublication = $this->getFieldArray('260', array('a'));
		$placesOfPublication2 = $this->getFieldArray('264', array('a'));
		return array_merge($placesOfPublication, $placesOfPublication2);
	}

	/**
	 * Get an array of playing times for the record (if applicable).
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getPlayingTimes()
	{
		$times = $this->getFieldArray('306', array('a'), false);

		// Format the times to include colons ("HH:MM:SS" format).
		for ($x = 0; $x < count($times); $x++) {
			$times[$x] = substr($times[$x], 0, 2) . ':' .
			substr($times[$x], 2, 2) . ':' .
			substr($times[$x], 4, 2);
		}

		return $times;
	}

	/**
	 * Get credits of people involved in production of the item.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getProductionCredits()
	{
		return $this->getFieldArray('508');
	}

	/**
	 * Get an array of publication frequency information.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getPublicationFrequency()
	{
		return $this->getFieldArray('310', array('a', 'b'));
	}

	/**
	 * Get an array of strings describing relationships to other items.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getRelationshipNotes()
	{
		return $this->getFieldArray('580');
	}

	/**
	 * Get an array of all series names containing the record.  Array entries may
	 * be either the name string, or an associative array with 'name' and 'number'
	 * keys.
	 *
	 * @access  public
	 * @return  array
	 */
	public function getSeries()	{
		$seriesInfo = $this->getGroupedWorkDriver()->getSeries();
		if (count($seriesInfo) == 0){
			// First check the 440, 800 and 830 fields for series information:
			$primaryFields = array(
					'440' => array('a', 'p'),
					'800' => array('a', 'b', 'c', 'd', 'f', 'p', 'q', 't'),
					'830' => array('a', 'p'));
			$matches = $this->getSeriesFromMARC($primaryFields);
			if (!empty($matches)) {
				return $matches;
			}

			// Now check 490 and display it only if 440/800/830 were empty:
			$secondaryFields = array('490' => array('a'));
			$matches = $this->getSeriesFromMARC($secondaryFields);
			if (!empty($matches)) {
				return $matches;
			}
		}
		return $seriesInfo;
	}

	/**
	 * Support method for getSeries() -- given a field specification, look for
	 * series information in the MARC record.
	 *
	 * @access  private
	 * @param   $fieldInfo  array           Associative array of field => subfield
	 *                                      information (used to find series name)
	 * @return  array                       Series data (may be empty)
	 */
	private function getSeriesFromMARC($fieldInfo){
		$matches = array();

		// Loop through the field specification....
		foreach($fieldInfo as $field => $subfields) {
			// Did we find any matching fields?
			$series = $this->getMarcRecord()->getFields($field);
			if (is_array($series)) {
				foreach($series as $currentField) {
					// Can we find a name using the specified subfield list?
					$name = $this->getSubfieldArray($currentField, $subfields);
					if (isset($name[0])) {
						$currentArray = array('name' => $name[0]);

						// Can we find a number in subfield v?  (Note that number is
						// always in subfield v regardless of whether we are dealing
						// with 440, 490, 800 or 830 -- hence the hard-coded array
						// rather than another parameter in $fieldInfo).
						$number = $this->getSubfieldArray($currentField, array('v'));
						if (isset($number[0])) {
							$currentArray['number'] = $number[0];
						}

						// Save the current match:
						$matches[] = $currentArray;
					}
				}
			}
		}

		return $matches;
	}

	/**
	 * Return an array of non-empty subfield values found in the provided MARC
	 * field.  If $concat is true, the array will contain either zero or one
	 * entries (empty array if no subfields found, subfield values concatenated
	 * together in specified order if found).  If concat is false, the array
	 * will contain a separate entry for each subfield value found.
	 *
	 * @access  private
	 * @param   object      $currentField   $result from File_MARC::getFields.
	 * @param   array       $subfields      The MARC subfield codes to read
	 * @param   bool        $concat         Should we concatenate subfields?
	 * @return  array
	 */
	private function getSubfieldArray($currentField, $subfields, $concat = true)
	{
		// Start building a line of text for the current field
		$matches = array();
		$currentLine = '';

		// Loop through all specified subfields, collecting results:
		foreach($subfields as $subfield) {
			/** @var File_MARC_Subfield[] $subfieldsResult */
			$subfieldsResult = $currentField->getSubfields($subfield);
			if (is_array($subfieldsResult)) {
				foreach($subfieldsResult as $currentSubfield) {
					// Grab the current subfield value and act on it if it is
					// non-empty:
					$data = trim($currentSubfield->getData());
					if (!empty($data)) {
						// Are we concatenating fields or storing them separately?
						if ($concat) {
							$currentLine .= $data . ' ';
						} else {
							$matches[] = $data;
						}
					}
				}
			}
		}

		// If we're in concat mode and found data, it will be in $currentLine and
		// must be moved into the matches array.  If we're not in concat mode,
		// $currentLine will always be empty and this code will be ignored.
		if (!empty($currentLine)) {
			$matches[] = trim($currentLine);
		}

		// Send back our $result array:
		return $matches;
	}

	/**
	 * @param File_MARC_Data_Field $marcField
	 * @param File_MARC_Subfield $subField
	 * @return string
	 */
	public function getSubfieldData($marcField, $subField){
		if ($marcField){
			return $marcField->getSubfield($subField) ? $marcField->getSubfield($subField)->getData() : '';
		}else{
			return '';
		}
	}

	/**
	 * Get an array of summary strings for the record.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getSummary()
	{
		return $this->getFieldArray('520');
	}

	/**
	 * Get an array of technical details on the item represented by the record.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getSystemDetails()
	{
		return $this->getFieldArray('538');
	}

	/**
	 * Get an array of note about the record's target audience.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getTargetAudienceNotes()
	{
		return $this->getFieldArray('521');
	}

	/**
	 * Get the full title of the record.
	 *
	 * @return  string
	 */
	public function getTitle()
	{
		return $this->getFirstFieldValue('245', array('a', 'b'));
	}

	/**
	 * Get the uniform title of the record.
	 *
	 * @return  array
	 */
	public function getUniformTitle()
	{
		return $this->getFieldArray('240', array('a', 'd', 'f', 'g', 'h', 'k', 'l', 'm', 'n', 'o', 'p', 'r', 's'));
	}

	/**
	 * Get the full title of the record.
	 *
	 * @return  string
	 */
	public function getShortTitle()
	{
		return $this->getFirstFieldValue('245', array('a'));
	}

	/**
	 * Get the full title of the record.
	 *
	 * @return  string
	 */
	public function getSortableTitle()
	{
		/** @var File_MARC_Data_Field $titleField */
		$titleField = $this->getMarcRecord()->getField('245');
		if ($titleField != null && $titleField->getSubfield('a') != null){
			$untrimmedTitle = $titleField->getSubfield('a')->getData();
			$charsToTrim = $titleField->getIndicator(2);
			return substr($untrimmedTitle, $charsToTrim);
		}
		return 'Unknown';
	}

	/**
	 * Get the title of the record.
	 *
	 * @return  string
	 */
	public function getSubtitle()
	{
		return $this->getFirstFieldValue('245', array('b'));
	}

	/**
	 * Get the text of the part/section portion of the title.
	 *
	 * @access  protected
	 * @return  string
	 */
	public function getTitleSection()
	{
		return $this->getFirstFieldValue('245', array('n', 'p'));
	}

	/**
	 * Get the statement of responsibility that goes with the title (i.e. "by John Smith").
	 *
	 * @access  protected
	 * @return  string
	 */
	protected function getTitleStatement()
	{
		return $this->getFirstFieldValue('245', array('c'));
	}

	/**
	 * Return an associative array of URLs associated with this record (key = URL,
	 * value = description).
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getURLs()
	{
		$retVal = array();

		/** @var File_MARC_Data_Field[] $urls */
		$urls = $this->getMarcRecord()->getFields('856');
		if ($urls) {
			foreach($urls as $url) {
				// Is there an address in the current field?
				/** @var File_MARC_Subfield $address */
				$address = $url->getSubfield('u');
				if ($address) {
					$addressStr = $address->getData();

					// Is there a description?  If not, just use the URL itself.
					/** @var File_MARC_Subfield $desc */
					$desc = $url->getSubfield('z');
					if ($desc) {
						$desc = $desc->getData();
					} else {
						$desc = $address;
					}

					$retVal[$addressStr] = $desc;
				}
			}
		}

		return $retVal;
	}

	/**
	 * Redirect to the RefWorks site and then die -- support method for getExport().
	 *
	 * @access  protected
	 */
	protected function redirectToRefWorks()
	{
		global $configArray;

		// Build the URL to pass data to RefWorks:
		$exportUrl = $configArray['Site']['url'] . '/Record/' .
		urlencode($this->getUniqueID()) . '/Export?style=refworks_data';

		// Build up the RefWorks URL:
		$url = $configArray['RefWorks']['url'] . '/express/expressimport.asp';
		$url .= '?vendor=' . urlencode($configArray['RefWorks']['vendor']);
		$url .= '&filter=RefWorks%20Tagged%20Format&url=' . urlencode($exportUrl);

		header("Location: {$url}");
		die();
	}

	public function getPrimaryAuthor(){
		return $this->getAuthor();
	}

	public function getAuthor(){
		if (isset($this->fields['auth_author'])){
			return $this->fields['auth_author'];
		}else{
			$author = $this->getFirstFieldValue('100', array('a'));
			if (empty($author )){
				$author = $this->getFirstFieldValue('110', array('a', 'b'));
			}
			return $author;
		}
	}

	protected function getSecondaryAuthors()
	{
		return $this->getContributors();
	}

	public function getContributors(){
		return $this->getFieldArray(700, array('a', 'b', 'c', 'd'));
	}

	public function getDetailedContributors(){
		$contributors = array();
		/** @var File_MARC_Data_Field[] $sevenHundredFields */
		$sevenHundredFields = $this->getMarcRecord()->getFields('700|710', true);
		foreach($sevenHundredFields as $field){
			$contributors[] = array(
				'name' => reset($this->getSubfieldArray($field, array('a', 'b', 'c', 'd'), true)),
				'role' => $field->getSubfield('4') != null ? mapValue('contributor_role', $field->getSubfield('4')->getData()) : '',
				'title' => reset($this->getSubfieldArray($field, array('t', 'm', 'n', 'r'), true)),
			);
		}
		$this->getFieldArray(700, array('a', 'b', 'c', 'd'));
		return $contributors;
	}

	/**
	 * Get the text to represent this record in the body of an email.
	 *
	 * @access  public
	 * @return  string              Text for inclusion in email.
	 */
	public function getEmail()
	{
		global $configArray;

		// Get Holdings
		try {
			/** @var CatalogConnection $catalog */
			$catalog = CatalogFactory::getCatalogConnectionInstance();;
		} catch (PDOException $e) {
			return new PEAR_Error('Cannot connect to ILS');
		}
		$holdingsSummary = $catalog->getStatusSummary($_GET['id']);
		if (PEAR_Singleton::isError($holdingsSummary)) {
			return $holdingsSummary;
		}

		$email = "  " . $this->getTitle() . "\n";
		if (isset($holdingsSummary['callnumber'])){
			$email .= "  Call Number: " . $holdingsSummary['callnumber'] . "\n";
		}
		if (isset($holdingsSummary['availableAt'])){
			$email .= "  Available At: " . $holdingsSummary['availableAt'] . "\n";
		}
		if (isset($holdingsSummary['downloadLink'])){
			$email .= "  Download from: " . $holdingsSummary['downloadLink'] . "\n";
		}


		return $email;
	}

	function getDescriptionFast(){
		/** @var File_MARC_Data_Field $descriptionField */
		$descriptionField = $this->getMarcRecord()->getField('520');
		if ($descriptionField != null && $descriptionField->getSubfield('a') != null){
			return $descriptionField->getSubfield('a')->getData();
		}
		return null;
	}

	function getDescription(){
		global $interface;
		global $library;

		$useMarcSummary = true;
		$summary = '';
		$isbn = $this->getCleanISBN();
		$upc = $this->getCleanUPC();
		if ($isbn || $upc){
			if (!$library || ($library && $library->preferSyndeticsSummary == 1)){
				require_once ROOT_DIR  . '/Drivers/marmot_inc/GoDeeperData.php';
				$summaryInfo = GoDeeperData::getSummary($isbn, $upc);
				if (isset($summaryInfo['summary'])){
					$summary = $summaryInfo['summary'];
					$useMarcSummary = false;
				}
			}
		}
		if ($useMarcSummary){
			if ($summaryFields = $this->marcRecord->getFields('520')) {
				$summary = '';
				foreach($summaryFields as $summaryField){
					$summary .= '<p>' . $this->getSubfieldData($summaryField, 'a') . '</p>';
				}
				$interface->assign('summary', $summary);
				$interface->assign('summaryTeaser', strip_tags($summary));
			}elseif ($library && $library->preferSyndeticsSummary == 0){
				require_once ROOT_DIR  . '/Drivers/marmot_inc/GoDeeperData.php';
				$summaryInfo = GoDeeperData::getSummary($isbn, $upc);
				if (isset($summaryInfo['summary'])){
					$summary = $summaryInfo['summary'];
				}
			}
		}
		if (strlen($summary) == 0){
			$summary = $this->getGroupedWorkDriver()->getDescriptionFast();
		}

		return $summary;
	}

	/**
	 * @param File_MARC_Record $marcRecord
	 * @param bool $allowExternalDescription
	 * @return array|string
	 */
	function loadDescriptionFromMarc($marcRecord, $allowExternalDescription = true){
		/** @var Memcache $memCache */
		global $memCache;
		global $configArray;

		if (!$this->getMarcRecord()){
			$descriptionArray = array();
			$description = "Description Not Provided";
			$descriptionArray['description'] = $description;
			return $descriptionArray;
		}

		// Get ISBN for cover and review use
		$isbn = null;
		/** @var File_MARC_Data_Field[] $isbnFields */
		if ($isbnFields = $marcRecord->getFields('020')) {
			//Use the first good ISBN we find.
			foreach ($isbnFields as $isbnField){
				if ($isbnSubfieldA = $isbnField->getSubfield('a')) {
					$tmpIsbn = trim($isbnSubfieldA->getData());
					if (strlen($tmpIsbn) > 0){
						$pos = strpos($tmpIsbn, ' ');
						if ($pos > 0) {
							$tmpIsbn = substr($tmpIsbn, 0, $pos);
						}
						$tmpIsbn = trim($tmpIsbn);
						if (strlen($tmpIsbn) > 0){
							if (strlen($tmpIsbn) < 10){
								$tmpIsbn = str_pad($tmpIsbn, 10, "0", STR_PAD_LEFT);
							}
							$isbn = $tmpIsbn;
							break;
						}
					}
				}
			}
		}

		$upc = null;
		/** @var File_MARC_Data_Field $upcField */
		if ($upcField = $marcRecord->getField('024')) {
			if ($upcSubfield = $upcField->getSubfield('a')) {
				$upc = trim($upcSubfield->getData());
			}
		}

		$descriptionArray = $memCache->get("record_description_{$isbn}_{$upc}_{$allowExternalDescription}");
		if (!$descriptionArray){
			$marcDescription = null;
			/** @var File_MARC_Data_Field $descriptionField */
			if ($descriptionField = $marcRecord->getField('520')) {
				if ($descriptionSubfield = $descriptionField->getSubfield('a')) {
					$description = trim($descriptionSubfield->getData());
					$marcDescription = $this->trimDescription($description);
				}
			}

			//Load the description
			//Check to see if there is a description in Syndetics and use that instead if available
			$useMarcSummary = true;
			if ($allowExternalDescription){
				if (!is_null($isbn) || !is_null($upc)){
					require_once ROOT_DIR . '/Drivers/marmot_inc/GoDeeperData.php';
					$summaryInfo = GoDeeperData::getSummary($isbn, $upc);
					if (isset($summaryInfo['summary'])){
						$descriptionArray['description'] = $this->trimDescription($summaryInfo['summary']);
						$useMarcSummary = false;
					}
				}
			}

			if ($useMarcSummary){
				if ($marcDescription != null){
					$descriptionArray['description'] = $marcDescription;
				}else{
					$description = "Description Not Provided";
					$descriptionArray['description'] = $description;
				}
			}

			$memCache->set("record_description_{$isbn}_{$upc}_{$allowExternalDescription}", $descriptionArray, 0, $configArray['Caching']['record_description']);
		}
		return $descriptionArray;
	}

	private function trimDescription($description){
			$chars = 300;
			if (strlen($description)>$chars){
					$description = $description." ";
					$description = substr($description,0,$chars);
					$description = substr($description,0,strrpos($description,' '));
					$description = $description . "...";
				}
		return $description;
	}

	function getLanguage(){
		/** @var File_MARC_Control_Field $field008 */
		$field008 = $this->getMarcRecord()->getField('008');
		if ($field008 != null && strlen($field008->getData() >= 37)){
			$languageCode = substr($field008->getData(), 35, 3);
			if ($languageCode == 'eng'){
				$languageCode = "English";
			}elseif ($languageCode == 'spa'){
				$languageCode = "Spanish";
			}
			return $languageCode;
		}else{
			return 'English';
		}
	}

	function getFormats(){
		return $this->getFormat();
	}

	/**
	 * Load the format for the record based off of information stored within the grouped work.
	 * Which was calculated at index time.
	 *
	 * @return string[]
	 */
	function getFormat(){
		//Rather than loading formats here, let's leverage the work we did at index time
		$recordDetails = $this->getGroupedWorkDriver()->getSolrField('record_details');
		if ($recordDetails){
			if (!is_array($recordDetails)){ $recordDetails = array($recordDetails); }
			foreach ($recordDetails as $recordDetailRaw){
				$recordDetail = explode('|', $recordDetailRaw);
				if ($recordDetail[0] == $this->getIdWithSource()){
					return array($recordDetail[1]);
				}
			}
			//We did not find a record for this in the index.  It's probably been deleted.
			return array('Unknown');
		}else{
			return array('Unknown');
		}
	}

	function getRecordUrl(){
		global $configArray;
		$recordId = $this->getUniqueID();

		return $configArray['Site']['path'] . "/{$this->indexingProfile->recordUrlComponent}/$recordId";
	}

	private $relatedRecords = null;
	function getRelatedRecords($realTimeStatusNeeded = true){
		if ($this->relatedRecords == null || isset($_REQUEST['reload'])){
			global $timer;
			global $interface;
			$this->relatedRecords = array();
			$recordId = $this->getUniqueID();

			$url = $this->getRecordUrl();

			if ($this->detailedRecordInfoFromIndex){
				$format = $this->detailedRecordInfoFromIndex[1];
				$edition = $this->detailedRecordInfoFromIndex[2];
				$language = $this->detailedRecordInfoFromIndex[3];
				$publisher = $this->detailedRecordInfoFromIndex[4];
				$publicationDate = $this->detailedRecordInfoFromIndex[5];
				$physicalDescription = $this->detailedRecordInfoFromIndex[6];
				$timer->logTime("Finished loading information from indexed info for $recordId");
			}else{
				$publishers = $this->getPublishers();
				$publisher = count($publishers) >= 1 ? $publishers[0] : '';
				$publicationDates = $this->getPublicationDates();
				$publicationDate = count($publicationDates) >= 1 ? $publicationDates[0] : '';
				$physicalDescriptions = $this->getPhysicalDescriptions();
				$physicalDescription = count($physicalDescriptions) >= 1 ? $physicalDescriptions[0] : '';
				$format = reset($this->getFormat());
				$edition = $this->getEdition(true);
				$language = $this->getLanguage();
				$timer->logTime("Finished loading MARC information in getRelatedRecords $recordId");
			}

			$formatCategory = mapValue('format_category_by_format', $format);
			$items = $this->getItemsFast();
			$onOrderCopies = 0;
			$availableCopies = 0;
			$localAvailableCopies = 0;
			$branchAvailableCopies = 0;
			$localCopies = 0;
			$totalCopies = 0;
			$hasLocalItem = false;
			$groupedStatus = "Currently Unavailable";
			foreach ($items as $item){
				if ($item['availability'] == true){
					$availableCopies++;
				}
				if ($item['isLocalItem'] || $item['isLibraryItem']){
					$hasLocalItem = true;
					$localCopies++;
					if ($item['availability'] == true){
						$localAvailableCopies++;
					}
				}
				if ($item['isLocalItem'] ){
					if ($item['availability'] == true){
						$branchAvailableCopies++;
					}
				}
				if (isset($item['onOrderCopies']) && $item['onOrderCopies'] > 0){
					$onOrderCopies += $item['onOrderCopies'];
					$totalCopies += $item['onOrderCopies'];
				}else{
					$totalCopies++;
				}
				//Check to see if we got a better grouped status
				if (isset($item['groupedStatus'])){
					$statusRankings = array(
						'Currently Unavailable' => 1,
						'On Order' => 2,
						'Coming Soon' => 3,
						'Checked Out' => 4,
						'Library Use Only' => 5,
						'Available Online' => 6,
						'On Shelf' => 7
					);
					if ($item['groupedStatus'] != '' && array_key_exists($item['groupedStatus'], $statusRankings) && $statusRankings[$item['groupedStatus']] > $statusRankings[$groupedStatus]){
						$groupedStatus = $item['groupedStatus'];
					}
				}
			}
			if ($totalCopies == 0){
				return $this->relatedRecords;
			}
			$numHolds = 0;
			if ($realTimeStatusNeeded){
				$numHolds = $this->getNumHolds();
			}
			$relatedRecord = array(
					'id' => $recordId,
					'url' => $url,
					'format' => $format,
					'formatCategory' => $formatCategory,
					'edition' => $edition,
					'language' => $language,
					'publisher' => $publisher,
					'publicationDate' => $publicationDate,
					'physical' => $physicalDescription,
					'callNumber' => $this->getCallNumber(),
					'available' => $availableCopies > 0,
					'availableLocally' => $localAvailableCopies > 0,
					'availableHere' => $branchAvailableCopies > 0,
					'inLibraryUseOnly' => $this->isLibraryUseOnly(false),
					'availableCopies' => $availableCopies,
					'copies' => $totalCopies,
					'onOrderCopies' => $onOrderCopies,
					'localAvailableCopies' => $localAvailableCopies,
					'localCopies' => $localCopies,
					'numHolds' => $numHolds,
					'hasLocalItem' => $hasLocalItem,
					'holdRatio' => $totalCopies > 0 ? ($availableCopies + ($totalCopies - $numHolds) / $totalCopies) : 0,
					'locationLabel' => $this->getLocationLabel(),
					'shelfLocation' => $this->getShelfLocation(),
					'itemSummary' => $this->getItemSummary(),
					'groupedStatus' => $groupedStatus,
					'source' => 'ils',
					'actions' => $this->getAllActions()
			);
			if (isset($interface)){
				$showHoldButton = $interface->getVariable('displayingSearchResults') ? $interface->getVariable('showHoldButtonInSearchResults'): $interface->getVariable('showHoldButton');
			}else{
				$showHoldButton = false;
			}

			if ($this->isHoldable() && isset($interface) && $showHoldButton){
				$relatedRecord['actions'][] = array(
						'title' => 'Place Hold',
						'url' => '',
						'onclick' => "return VuFind.Record.showPlaceHold('{$this->getModule()}', '{$this->getIdWithSource()}');",
						'requireLogin' => false,
				);
			}
			$timer->logTime("Finished getRelatedRecords $recordId");
			$this->relatedRecords[] = $relatedRecord;
		}
		return $this->relatedRecords;
	}

	public function getRecordActions($isAvailable, $isHoldable, $isBookable, $relatedUrls = null){
		$actions = array();
		global $interface;
		global $library;
		if (isset($interface)){
			if ($interface->getVariable('displayingSearchResults')){
				$showHoldButton =  $interface->getVariable('showHoldButtonInSearchResults');
			}else{
				$showHoldButton = $interface->getVariable('showHoldButton');
			}
		}else{
			$showHoldButton = false;
		}
		if ($showHoldButton && $isAvailable){
			$showHoldButton = !$interface->getVariable('showHoldButtonForUnavailableOnly');
		}

		if ($isHoldable && $showHoldButton){
			$actions[] = array(
				'title' => 'Place Hold',
				'url' => '',
				'onclick' => "return VuFind.Record.showPlaceHold('{$this->getModule()}', '{$this->getIdWithSource()}');",
				'requireLogin' => false,
			);
		}
		if ($isBookable && $library->enableMaterialsBooking){
			$actions[] = array(
				'title' => 'Schedule Item',
				'url' => '',
				'onclick' => "return VuFind.Record.showBookMaterial('{$this->getModule()}', '{$this->getId()}');",
				'requireLogin' => false,
			);
		}

		return $actions;
	}

	protected function isHoldable(){
		$items = $this->getItemsFast();
		foreach ($items as $item){
			//Try to get an available non reserve call number
			if ($item['holdable'] == 1){
				return true;
			}
		}
		return false;
	}

	private function getLocationLabel(){
		$items = $this->getItemsFast();
		$locationLabel = null;
		foreach ($items as $item){
			//Try to get an available non reserve call number
			if ($item['isLocalItem']){
				return $item['locationLabel'];
			}else if ($item['isLibraryItem']){
				if ($locationLabel == null){
					$locationLabel = $item['locationLabel'];
				}
			}
		}
		return $locationLabel;
	}

	private function getShelfLocation(){
		$items = $this->getItemsFast();
		$locationLabel = null;
		foreach ($items as $item){
			//Try to get an available non reserve call number
			if ($item['isLocalItem']){
				return $item['shelfLocation'];
			}else if ($item['isLibraryItem']){
				if ($locationLabel == null){
					$locationLabel = $item['shelfLocation'];
				}
			}
		}
		return $locationLabel;
	}

	private function getItemSummary(){
		global $library;
		$searchLocation = Location::getSearchLocation();
		$itemSummary = array();
		$items = $this->getItemsFast();
		foreach ($items as $item){
			$description = $item['shelfLocation'] . ': ' . $item['callnumber'];
			if ($item['isLocalItem']){
				$key = '1 ' . $description;
			}elseif ($item['isLibraryItem']){
				$key = '2 ' . $description;
			}else{
				$key = '3 ' . $description;
			}

			$displayByDefault = false;
			if ($item['availability']){
				if ($searchLocation){
					$displayByDefault = $item['isLocalItem'];
				}elseif ($library){
					$displayByDefault = $item['isLibraryItem'];
				}
			}
			$itemInfo = array(
				'description' => $description,
				'shelfLocation' => $item['shelfLocation'],
				'callNumber' => $item['callnumber'],
				'totalCopies' => 1,
				'availableCopies' => $item['availability'] ? 1 : 0,
				'isLocalItem' => $item['isLocalItem'],
				'isLibraryItem' => $item['isLibraryItem'],
				'displayByDefault' => $displayByDefault,
				'onOrderCopies' => isset($item['onOrderCopies']) ? $item['onOrderCopies'] : 0,
			);
			if (isset($itemSummary[$key])){
				$itemSummary[$key]['totalCopies']++;
				$itemSummary[$key]['availableCopies']+=$itemInfo['availableCopies'];
				if ($itemInfo['displayByDefault']){
					$itemSummary[$key]['displayByDefault'] = true;
				}
				$itemSummary[$key]['onOrderCopies']+=$itemInfo['onOrderCopies'];
			}else{
				$itemSummary[$key] = $itemInfo;
			}
		}
		ksort($itemSummary);
		return $itemSummary;
	}

	public function isAvailable($realTime){
		if ($realTime){
			$items = $this->getItems();
		}else{
			$items = $this->getItemsFast();
		}
		foreach ($items as $item){
			//Try to get an available non reserve call number
			if ($item['availability'] === true){
				return true;
			}
		}
		return false;
	}

	public function isAvailableLocally($realTime){
		if ($realTime){
			$items = $this->getItems();
		}else{
			$items = $this->getItemsFast();
		}
		foreach ($items as $item){
			//Try to get an available non reserve call number
			if ($item['availability'] === true && ($item['isLocalItem'] || $item['isLibraryItem'])){
				return true;
			}
		}
		return false;
	}

	public function isLibraryUseOnly($realTime){
		if ($realTime){
			$items = $this->getItems();
		}else{
			$items = $this->getItemsFast();
		}
		$allLibraryUseOnly = true;
		foreach ($items as $item){
			//Try to get an available non reserve call number
			if (!isset($item['inLibraryUseOnly']) || !$item['inLibraryUseOnly']){
				$allLibraryUseOnly = false;
			}
		}
		return $allLibraryUseOnly;
	}

	protected function getAllActions() {
		$items = $this->getItemsFast();
		$allActions = array();
		foreach ($items as $item){
			if (isset($item['actions']) && count($item['actions'] > 0)){
				$allActions = array_merge($allActions, $item['actions']);
			}
		}
		return $allActions;
	}

	private function getCallNumber(){
		$items = $this->getItemsFast();
		$firstCallNumber = null;
		$nonLibraryCallNumber = null;
		foreach ($items as $item){
			if ($item['isLocalItem'] == true){
				return $item['callnumber'];
			}else if ($item['isLibraryItem'] == true){
				//Try to get an available non reserve call number
				if ($item['availability'] && $item['holdable']){
					return $item['callnumber'];
				}else if (is_null($firstCallNumber)){
					$firstCallNumber = $item['callnumber'];
				}
			}elseif ($item['holdable'] == true && is_null($nonLibraryCallNumber)){
				//Not at this library (system)
				//$nonLibraryCallNumber = $item['callnumber'] . '(' . $item['location'] . ')';
			}
		}
		if ($firstCallNumber != null){
			return $firstCallNumber;
		}elseif ($nonLibraryCallNumber != null){
			return $nonLibraryCallNumber;
		}else{
			return '';
		}

	}

	private $fastItems = null;
	public function getItemsFast(){
		global $timer;
		if ($this->fastItems == null || isset($_REQUEST['reload'])){
			if ($this->itemsFromIndex){
				$this->fastItems = array();
				foreach ($this->itemsFromIndex as $itemData){
					$isOrderItem = $itemData[7] == "true";
					$onOrderCopies = 0;
					if ($isOrderItem){
						$onOrderCopies = $itemData[6];
					}

					$groupedStatus = $itemData[13];
					$status = $itemData[14];
					$isHoldable = $itemData[17] == "true";
//					$isBookable = $itemData[17] == "true"; // TODO set to real value
					$inLibraryUseOnly = $itemData[19] == "true";

					//Old style where record and item information are combined
					$shelfLocation = $itemData[2];
					$callNumber = $itemData[3];

					$isLibraryItem = $itemData[20] == "true";
					$isLocalItem = $itemData[15] == "true";

					//Try to trim the courier code if any
					if (preg_match('/(.*?)\\sC\\d{3}\\w{0,2}$/', $shelfLocation, $locationParts)){
						$shelfLocation = $locationParts[1];
					}

					$this->fastItems[] = array(
						'callnumber' => $callNumber,
						'availability' => $itemData[16] == "true",
						'holdable' => $isHoldable,
//						'bookable' => $isBookable,
						'inLibraryUseOnly' => $inLibraryUseOnly,
						'isLibraryItem' => $isLibraryItem,
						'isLocalItem' => $isLocalItem,
						'locationLabel' => true,
						'shelfLocation' => $shelfLocation,
						'onOrderCopies' => $onOrderCopies,
						'status' => $status,
						'groupedStatus' => $groupedStatus,
					);
				}
				$timer->logTime("Finished getItemsFast for marc record based on data in index");
			}else{
				$driver = MarcRecord::getCatalogDriver();
				$this->fastItems = $driver->getItemsFast($this->getUniqueID(), $this->scopingEnabled, $this->getMarcRecord());
				$timer->logTime("Finished getItemsFast for marc record based on data in driver");
			}
		}
		return $this->fastItems;
	}

	private $items = null;
	public function getItems(){
		if ($this->items == null){
			$driver = MarcRecord::getCatalogDriver();
			$this->items = $driver->getStatus($this->getUniqueID(), true);
		}
		return $this->items;
	}

	static $catalogDriver = null;

	/**
	 * @return Millennium|Sierra|Marmot|DriverInterface|HorizonAPI
	 */
	protected static function getCatalogDriver(){
		if (MarcRecord::$catalogDriver == null){
			global $configArray;
			try {
				require_once ROOT_DIR . '/CatalogFactory.php';
				MarcRecord::$catalogDriver = CatalogFactory::getCatalogConnectionInstance();
			} catch (PDOException $e) {
				// What should we do with this error?
				if ($configArray['System']['debug']) {
					echo '<pre>';
					echo 'DEBUG: ' . $e->getMessage();
					echo '</pre>';
				}
				return null;
			}
		}
		return MarcRecord::$catalogDriver;
	}

	/**
	 * Get an array of physical descriptions of the item.
	 *
	 * @access  protected
	 * @return  array
	 */
	public function getPhysicalDescriptions()
	{
		$physicalDescription1 = $this->getFieldArray("300", array('a', 'b', 'c', 'e', 'f', 'g'));
		$physicalDescription2 = $this->getFieldArray("530", array('a', 'b', 'c', 'd'));
		return array_merge($physicalDescription1, $physicalDescription2);
	}

	/**
	 * Get the publication dates of the record.  See also getDateSpan().
	 *
	 * @access  public
	 * @return  array
	 */
	public function getPublicationDates()
	{
		$publicationDates = $this->getFieldArray('260', array('c'));
		/** @var File_MARC_Data_Field[] $rdaPublisherFields */
		$rdaPublisherFields = $this->getMarcRecord()->getFields('264');
		foreach ($rdaPublisherFields as $rdaPublisherField){
			if ($rdaPublisherField->getIndicator(2) == 1 && $rdaPublisherField->getSubfield('c') != null){
				$publicationDates[] = $rdaPublisherField->getSubfield('c')->getData();
			}
		}
		foreach ($publicationDates as $key => $publicationDate){
			$publicationDates[$key] = preg_replace('/[.,]$/', '', $publicationDate);
		}
		return $publicationDates;
	}

	/**
	 * Get the publishers of the record.
	 *
	 * @access  protected
	 * @return  array
	 */
	protected function getPublishers()
	{
		$publishers = $this->getFieldArray('260', array('b'));
		/** @var File_MARC_Data_Field[] $rdaPublisherFields */
		$rdaPublisherFields = $this->getMarcRecord()->getFields('264');
		foreach ($rdaPublisherFields as $rdaPublisherField){
			if ($rdaPublisherField->getIndicator(2) == 1 && $rdaPublisherField->getSubfield('b') != null){
				$publishers[] = $rdaPublisherField->getSubfield('b')->getData();
			}
		}
		foreach ($publishers as $key => $publisher){
			$publishers[$key] = preg_replace('/[.,]$/', '', $publisher);
		}
		return $publishers;
	}

	private $isbns = null;
	/**
	 * Get an array of all ISBNs associated with the record (may be empty).
	 *
	 * @access  protected
	 * @return  array
	 */
	public function getISBNs()
	{
		if ($this->isbns == null){
			// If ISBN is in the index, it should automatically be an array... but if
			// it's not set at all, we should normalize the value to an empty array.
			if (isset($this->fields['isbn'])){
				if (is_array($this->fields['isbn'])){
					$this->isbns = $this->fields['isbn'];
				}else{
					$this->isbns = array($this->fields['isbn']);
				}
			}else{
				$isbns = array();
				/** @var File_MARC_Data_Field[] $isbnFields */
				$isbnFields = $this->getMarcRecord()->getFields('020');
				foreach($isbnFields as $isbnField){
					if ($isbnField->getSubfield('a') != null){
						$isbns[] = $isbnField->getSubfield('a')->getData();
					}
				}
				$this->isbns = $isbns;
			}
		}
		return $this->isbns;
	}

	/**
	 * Get the UPC associated with the record (may be empty).
	 *
	 * @return  array
	 */
	public function getUPCs()
	{
		// If UPCs is in the index, it should automatically be an array... but if
		// it's not set at all, we should normalize the value to an empty array.
		if (isset($this->fields['upc'])){
			if (is_array($this->fields['upc'])){
				return $this->fields['upc'];
			}else{
				return array($this->fields['upc']);
			}
		}else{
			$upcs = array();
			/** @var File_MARC_Data_Field[] $upcFields */
			$upcFields = $this->getMarcRecord()->getFields('024');
			foreach($upcFields as $upcField){
				if ($upcField->getSubfield('a') != null){
					$upcs[] = $upcField->getSubfield('a')->getData();
				}
			}
			return $upcs;
		}
	}

	public function getAcceleratedReaderData(){
		return $this->getGroupedWorkDriver()->getAcceleratedReaderData();
	}
	public function getLexileCode(){
		return $this->getGroupedWorkDriver()->getLexileCode();
	}
	public function getLexileScore(){
		return $this->getGroupedWorkDriver()->getLexileScore();
	}

	public function getMoreDetailsOptions(){
		global $interface;

		$isbn = $this->getCleanISBN();

		//Load table of contents
		$tableOfContents = $this->getTOC();
		$interface->assign('tableOfContents', $tableOfContents);

		//Load more details options
		$moreDetailsOptions = $this->getBaseMoreDetailsOptions($isbn);
		$moreDetailsOptions['copies'] = array(
			'label' => 'Copies',
			'body' => '<div id="holdingsPlaceholder"></div>',
			'openByDefault' => true
		);
		//Other editions if applicable (only if we aren't the only record!)
		$relatedRecords = $this->getGroupedWorkDriver()->getRelatedRecords();
		if (count($relatedRecords) > 1){
			$interface->assign('relatedManifestations', $this->getGroupedWorkDriver()->getRelatedManifestations());
			$moreDetailsOptions['otherEditions'] = array(
					'label' => 'Other Editions',
					'body' => $interface->fetch('GroupedWork/relatedManifestations.tpl'),
					'hideByDefault' => false
			);
		}
		$moreDetailsOptions['moreDetails'] = array(
			'label' => 'More Details',
			'body' => $interface->fetch('Record/view-more-details.tpl'),
		);
		$this->loadSubjects();
		$moreDetailsOptions['subjects'] = array(
				'label' => 'Subjects',
				'body' => $interface->fetch('Record/view-subjects.tpl'),
		);
		$moreDetailsOptions['citations'] = array(
			'label' => 'Citations',
			'body' => $interface->fetch('Record/cite.tpl'),
		);
		if ($interface->getVariable('showStaffView')){
			$moreDetailsOptions['staff'] = array(
				'label' => 'Staff View',
				'body' => $interface->fetch($this->getStaffView()),
			);
		}

		return $this->filterAndSortMoreDetailsOptions($moreDetailsOptions);
	}

	public function loadSubjects(){
		global $interface;
		global $configArray;
		$marcRecord = $this->getMarcRecord();
		if (isset($configArray['Content']['subjectFieldsToShow'])){
			$subjectFieldsToShow = $configArray['Content']['subjectFieldsToShow'];
			$subjectFields = explode(',', $subjectFieldsToShow);

			$subjects = array();
			$standardSubjects = array();
			$bisacSubjects = array();
			$oclcFastSubjects = array();
			foreach ($subjectFields as $subjectField){
				/** @var File_MARC_Data_Field[] $marcFields */
				$marcFields = $marcRecord->getFields($subjectField);
				if ($marcFields){
					foreach ($marcFields as $marcField){
						$searchSubject = "";
						$subject = array();
						//Determine the type of the subject
						$type = 'standard';
						$subjectSource = $marcField->getSubfield('2');
						if ($subjectSource != null){
							if (preg_match('/bisac/i', $subjectSource->getData())){
								$type = 'bisac';
							}elseif (preg_match('/fast/i', $subjectSource->getData())){
								$type = 'fast';
							}
						}

						foreach ($marcField->getSubFields() as $subField){
							/** @var File_MARC_Subfield $subField */
							if ($subField->getCode() != '2' && $subField->getCode() != '0'){
								$subFieldData = $subField->getData();
								if ($type == 'bisac' && $subField->getCode() == 'a'){
									$subFieldData = ucwords(strtolower($subFieldData));
								}
								$searchSubject .= " " . $subFieldData;
								$subject[] = array(
										'search' => trim($searchSubject),
										'title'  => $subFieldData,
								);
							}
						}
						if ($type == 'bisac'){
							$bisacSubjects[] = $subject;
							$subjects[] = $subject;
						}elseif ($type == 'fast'){
							//Suppress fast subjects by default
							$oclcFastSubjects[] = $subject;
						}else{
							$subjects[] = $subject;
							$standardSubjects[] = $subject;
						}

					}
				}
			}
			$interface->assign('subjects', $subjects);
			$interface->assign('standardSubjects', $standardSubjects);
			$interface->assign('bisacSubjects', $bisacSubjects);
			$interface->assign('oclcFastSubjects', $oclcFastSubjects);
		}
	}

	protected function getRecordType(){
		if ($this->profileType){
			return $this->profileType;
		}else{
			return 'ils';
		}
	}

	/**
	 * @return File_MARC_Record
	 */
	public function getMarcRecord(){
		if ($this->marcRecord == null){
			$this->marcRecord = MarcLoader::loadMarcRecordByILSId("{$this->profileType}:{$this->id}");
			global $timer;
			$timer->logTime("Finished loading marc record for {$this->id}");
		}
		return $this->marcRecord;
	}

	/**
	 * @param File_MARC_Data_Field[] $allFields
	 * @return array
	 */
	function processTableOfContentsFields($allFields){
		$notes = array();
		foreach ($allFields as $marcField){
			$curNote = '';
			/** @var File_MARC_Subfield $subfield */
			foreach ($marcField->getSubfields() as $subfield){
				$note = $subfield->getData();
				$curNote .= " " . $note;
				$curNote = trim($curNote);
//				if (strlen($curNote) > 0 && in_array($subfield->getCode(), array('t', 'a'))){
//					$notes[] = $curNote;
//					$curNote = '';
//				}
// 20131112 split 505 contents notes on double-hyphens instead of title subfields (which created bad breaks mis-associating titles and authors)
				if (preg_match("/--$/",$curNote)) {
					$notes[] = $curNote;
					$curNote = '';
				}elseif (strpos($curNote, '--') !== false){
					$brokenNotes = explode('--', $curNote);
					$notes = array_merge($notes, $brokenNotes);
					$curNote = '';
				}
			}
			if ($curNote != ''){
				$notes[] = $curNote;
			}
		}
		return $notes;
	}

	private $numHolds = -1;
	function getNumHolds() {
		if ($this->numHolds != -1){
			return $this->numHolds;
		}
		global $configArray;
		global $timer;
		if ($configArray['Catalog']['ils'] == 'Horizon'){
			require_once ROOT_DIR . '/CatalogFactory.php';
			$catalog = CatalogFactory::getCatalogConnectionInstance();;
			$this->numHolds = $catalog->getNumHolds($this->getUniqueID());
		}else{

			require_once ROOT_DIR . '/Drivers/marmot_inc/IlsHoldSummary.php';
			$holdSummary = new IlsHoldSummary();
			$holdSummary->ilsId = $this->getUniqueID();
			if ($holdSummary->find(true)){
				$this->numHolds = $holdSummary->numHolds;
			}else{
				$this->numHolds = 0;
			}
		}

		$timer->logTime("Loaded number of holds");
		return $this->numHolds;
	}

	function getNotes(){
		$additionalNotesFields = array(
			'520' => 'Description',
			'500' => 'General Note',
			'504' => 'Bibliography',
			'511' => 'Participants/Performers',
			'518' => 'Date/Time and Place of Event',
			'310' => 'Current Publication Frequency',
			'321' => 'Former Publication Frequency',
			'351' => 'Organization & arrangement of materials',
			'362' => 'Dates of publication and/or sequential designation',
			'501' => '"With"',
			'502' => 'Dissertation',
			'506' => 'Restrictions on Access',
			'507' => 'Scale for Graphic Material',
			'508' => 'Creation/Production Credits',
			'510' => 'Citation/References',
			'513' => 'Type of Report an Period Covered',
			'515' => 'Numbering Peculiarities',
			'521' => 'Target Audience',
			'522' => 'Geographic Coverage',
			'525' => 'Supplement',
			'526' => 'Study Program Information',
			'530' => 'Additional Physical Form',
			'533' => 'Reproduction',
			'534' => 'Original Version',
			'536' => 'Funding Information',
			'538' => 'System Details',
			'545' => 'Biographical or Historical Data',
			'546' => 'Language',
			'547' => 'Former Title Complexity',
			'550' => 'Issuing Body',
			'555' => 'Cumulative Index/Finding Aids',
			'556' => 'Information About Documentation',
			'561' => 'Ownership and Custodial History',
			'563' => 'Binding Information',
			'580' => 'Linking Entry Complexity',
			'581' => 'Publications About Described Materials',
			'586' => 'Awards',
			'590' => 'Local note',
			'599' => 'Differentiable Local note',
		);

		$notes = array();
		foreach ($additionalNotesFields as $tag => $label){
			/** @var File_MARC_Data_Field[] $marcFields */
			$marcFields = $this->marcRecord->getFields($tag);
			foreach ($marcFields as $marcField){
				$noteText = array();
				foreach ($marcField->getSubFields() as $subfield){
					/** @var File_MARC_Subfield $subfield */
					$noteText[] = $subfield->getData();
				}
				$note = implode(',', $noteText);
				if (strlen($note) > 0){
					$notes[] = array('label' => $label, 'note' => $note);
				}
			}
		}
		return $notes;
	}
}
