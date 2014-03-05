<?php
/**
 *
 * Copyright (C) Villanova University 2009.
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

/**
 * Support function -- get the file path to one of the ini files specified in the
 * [Extra_Config] section of config.ini.
 *
 * @param   string $name        The ini's name from the [Extra_Config] section of config.ini
 * @return  string      The file path
 */
function getExtraConfigArrayFile($name)
{
	global $configArray;

	// Load the filename from config.ini, and use the key name as a default
	//     filename if no stored value is found.
	$filename = isset($configArray['Extra_Config'][$name]) ? $configArray['Extra_Config'][$name] : $name . '.ini';

	//Check to see if there is a domain name based subfolder for he configuration
	global $serverName;
	if (file_exists("../../sites/$serverName/conf/$filename")){
		// Return the file path (note that all ini files are in the conf/ directory)
		return "../../sites/$serverName/conf/$filename";
	}elseif (file_exists("../../sites/default/conf/$filename")){
		// Return the file path (note that all ini files are in the conf/ directory)
		return "../../sites/default/conf/$filename";
	} else{
		// Return the file path (note that all ini files are in the conf/ directory)
		return '../../sites/' . $filename;
	}

}

/**
 * Load a translation map from the translation_maps directory
 *
 * @param   string $name        The name of the translation map should not include _map.properties
 * @return  string      The file path
 */
function getTranslationMap($name)
{
	//Check to see if there is a domain name based subfolder for he configuration
	global $serverName;
	$mapFilename = '';
	$mapNameFull = $name . '_map.properties';
	if (file_exists("../../sites/$serverName/translation_maps/$mapNameFull")){
		// Return the file path (note that all ini files are in the conf/ directory)
		$mapFilename = "../../sites/$serverName/translation_maps/$mapNameFull";
	}elseif (file_exists("../../sites/default/translation_maps/$mapNameFull")){
		// Return the file path (note that all ini files are in the conf/ directory)
		$mapFilename = "../../sites/default/translation_maps/$mapNameFull";
	} else{
		// Return the file path (note that all ini files are in the conf/ directory)
		$mapFilename = '../../sites/' . $mapNameFull;
	}

	static $translationMaps = array();

	// If the requested settings aren't loaded yet, pull them in:
	if (!isset($translationMaps[$name])) {
		// Try to load the .ini file; if loading fails, the file probably doesn't
		// exist, so we can treat it as an empty array.
		$translationMaps[$name] = array();
		$fHnd = fopen($mapFilename, 'r');
		while (($line = fgets($fHnd)) !== false){
			if (substr($line, 0, 1) == '#'){
				//skip the line, it's a comment
			}else{
				$lineData = explode('=', $line, 2);
				if (count($lineData) == 2){
					$translationMaps[$name][trim($lineData[0])] = trim($lineData[1]);
				}
			}
		}
		fclose($fHnd);
	}

	return $translationMaps[$name];
}

function mapValue($mapName, $value){
	$map = getTranslationMap($mapName);
	if (isset($map[$value])){
		return $map[$value];
	}elseif(isset($map['*'])){
		return $map['*'];
	}else{
		return '';
	}
}

/**
 * Support function -- get the contents of one of the ini files specified in the
 * [Extra_Config] section of config.ini.
 *
 * @param   string $name        The ini's name from the [Extra_Config] section of config.ini
 * @return  array       The retrieved configuration settings.
 */
function getExtraConfigArray($name)
{
	static $extraConfigs = array();

	// If the requested settings aren't loaded yet, pull them in:
	if (!isset($extraConfigs[$name])) {
		// Try to load the .ini file; if loading fails, the file probably doesn't
		// exist, so we can treat it as an empty array.
		$extraConfigs[$name] = @parse_ini_file(getExtraConfigArrayFile($name), true);
		if ($extraConfigs[$name] === false) {
			$extraConfigs[$name] = array();
		}

		if ($name == 'facets'){
			//*************************
			//Marmot overrides for controlling facets based on library system.
			/** @var $librarySingleton Library */
			global $librarySingleton;
			$library = $librarySingleton->getActiveLibrary();
			if (isset($library)){
				if ($library->restrictSearchByLibrary && $library->useScope){
					unset($extraConfigs[$name]['Results']['institution']);
					unset($extraConfigs[$name]['Author']['institution']);
				}
			}
			global $locationSingleton;
			$activeLocation = $locationSingleton->getActiveLocation();
			if (!is_null($activeLocation)){
				if ($activeLocation->restrictSearchByLocation && $activeLocation->useScope){
					unset($extraConfigs[$name]['Results']['institution']);
					unset($extraConfigs[$name]['Results']['building']);
					unset($extraConfigs[$name]['Author']['institution']);
					unset($extraConfigs[$name]['Author']['building']);
				}
			}
		}
	}

	return $extraConfigs[$name];
}

/**
 * Support function -- merge the contents of two arrays parsed from ini files.
 *
 * @param   array $config_ini  The base config array.
 * @param   array $custom_ini  Overrides to apply on top of the base array.
 * @return  array       The merged results.
 */
function ini_merge($config_ini, $custom_ini)
{
	foreach ($custom_ini as $k => $v) {
		if (is_array($v)) {
			$config_ini[$k] = ini_merge(isset($config_ini[$k]) ? $config_ini[$k] : array(), $custom_ini[$k]);
		} else {
			$config_ini[$k] = $v;
		}
	}
	return $config_ini;
}

/**
 * Support function -- load the main configuration options, overriding with
 * custom local settings if applicable.
 *
 * @return  array       The desired config.ini settings in array format.
 */
function readConfig()
{
	//Read default configuration file
	$configFile = '../../sites/default/conf/config.ini';
	$mainArray = parse_ini_file($configFile, true);

	global $serverName;
	$serverUrl = $_SERVER['SERVER_NAME'];
	$server = $serverUrl;
	$serverParts = explode('.', $server);
	$serverName = 'default';
	while (count($serverParts) > 0){
		$tmpServername = join('.', $serverParts);
		$configFile = "../../sites/$tmpServername/conf/config.ini";
		if (file_exists($configFile)){
			$serverArray = parse_ini_file($configFile, true);
			$mainArray = ini_merge($mainArray, $serverArray);
			$serverName = $tmpServername;
		}
		array_shift($serverParts);
	}

	// Sanity checking to make sure we loaded a good file
	// @codeCoverageIgnoreStart
	if ($serverName == 'default'){
		global $logger;
		if ($logger){
			$logger->log('Did not find servername for server ' . $_SERVER['SERVER_NAME'], PEAR_LOG_ERR);
		}
		PEAR_Singleton::raiseError("Invalid configuration, could not find site for " . $_SERVER['SERVER_NAME']);
	}

	if ($mainArray == false){
		echo("Unable to parse configuration file $configFile, please check syntax");
	}
	// @codeCoverageIgnoreEnd

	//If we are accessing the site via a subdomain, need to preserve the subdomain
	//Don't try to preserve SSL since the combination of proxy and SSL does not work nicely.
	//i.e. https://mesa.marmot.org is proxied to https://mesa.opac.marmot.org which does not have
	//a valid SSL cert
	$mainArray['Site']['url'] = "http://" . $serverUrl;

	return $mainArray;
}

/**
 * Update the configuration array as needed based on scoping rules defined
 * by the subdomain.
 *
 * @param array $configArray the existing main configuration options.
 *
 * @return array the configuration options adjusted based on the scoping rules.
 */
function updateConfigForScoping($configArray) {
	global $timer;
	//Get the subdomain for the request
	global $serverName;

	//split the servername based on
	global $subdomain;
	$subdomain = null;
	if(strpos($_SERVER['SERVER_NAME'], '.')){
		$serverComponents = explode('.', $_SERVER['SERVER_NAME']);
		if (count($serverComponents) >= 3){
			//URL is probably of the form subdomain.marmot.org or subdomain.opac.marmot.org
			$subdomain = $serverComponents[0];
		} else if (count($serverComponents) == 2){
			//URL could be either subdomain.localhost or marmot.org. Only use the subdomain
			//If the second component is localhost.
			if (strcasecmp($serverComponents[1], 'localhost') == 0){
				$subdomain = $serverComponents[0];
			}
		}
	}

	$timer->logTime('got subdomain');

	//Load the library system information
	global $library;
	global $locationSingleton;
	if (isset($_SESSION['library']) && isset($_SESSION['location'])){
		$library = $_SESSION['library'];
		$locationSingleton = $_SESSION['library'];
	}else{
		$Library = new Library();
		$Library->whereAdd("subdomain = '$subdomain'");
		$Library->find();


		if ($Library->N == 1) {
			$Library->fetch();
			//Make the library infroamtion global so we can work with it later.
			$library = $Library;
		}else{
			//The subdomain can also indicate a location.
			$Location = new Location();
			$Location->whereAdd("code = '$subdomain'");
			$Location->find();
			if ($Location->N == 1){
				$Location->fetch();
				//We found a location for the subdomain, get the library.
				/** @var Library $librarySingleton */
				global $librarySingleton;
				$library = $librarySingleton->getLibraryForLocation($Location->locationId);
				$locationSingleton->setActiveLocation(clone $Location);
			}
		}
	}
	if (isset($library) && $library != null){
		//Update the title
		$configArray['Site']['theme'] = $library->themeName . ',' . $configArray['Site']['theme'] . ',default';
		$configArray['Site']['title'] = $library->displayName;

		//Update the searches file
		if (strlen($library->searchesFile) > 0 && $library->searchesFile != 'default'){
			$file = trim("../../sites/$serverName/conf/searches/" . $library->searchesFile . '.ini');
			if (file_exists($file)) {
				$configArray['Extra_Config']['searches'] = 'searches/' . $library->searchesFile . '.ini';
			}
		}


		$location = $locationSingleton->getActiveLocation();

		//Add an extra css file for the location if it exists.
		$themes = explode(',', $library->themeName);
		foreach ($themes as $themeName){
			if ($location != null && file_exists('./interface/themes/' . $themeName . '/images/'. $location->code .'_logo_responsive.png')) {
				$configArray['Site']['responsiveLogo'] = '/interface/themes/' . $themeName . '/images/'. $location->code .'_logo_responsive.png';
			}
			if ($location != null && file_exists('./interface/themes/' . $themeName . '/images/'. $location->code .'_logo_small.png')) {
				$configArray['Site']['smallLogo'] = '/interface/themes/' . $themeName . '/images/'. $location->code .'_logo_small.png';
			}
			if ($location != null && file_exists('./interface/themes/' . $themeName . '/images/'. $location->code .'_logo_large.png')) {
				$configArray['Site']['largeLogo'] = '/interface/themes/' . $themeName . '/images/'. $location->code .'_logo_large.png';
			}
		}
	}
	$timer->logTime('finished update config for scoping');

	return $configArray;
}
