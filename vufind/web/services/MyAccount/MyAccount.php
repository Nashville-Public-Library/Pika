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

require_once ROOT_DIR . '/Action.php';

require_once ROOT_DIR . '/CatalogConnection.php';

abstract class MyAccount extends Action
{
	/** @var  SearchObject_Solr|SearchObject_Base */
	protected $db;
	/** @var  CatalogConnection $catalog */
	protected $catalog;
	protected $requireLogin = true;

	function __construct()
	{
		global $interface;
		global $configArray;
		global $user;

		$interface->assign('page_body_style', 'sidebar_left');

		if ($this->requireLogin && !UserAccount::isLoggedIn()) {
			require_once ROOT_DIR . '/services/MyAccount/Login.php';
			$myAccountAction = new MyAccount_Login();
			$myAccountAction->launch();
			exit();
		}

		// Setup Search Engine Connection
		$class = $configArray['Index']['engine'];
		$this->db = new $class($configArray['Index']['url']);

		// Connect to Database
		$this->catalog = CatalogFactory::getCatalogConnectionInstance($user->source);

		//Check to see if we have any acs or single use eContent in the catalog
		//to enable the holds and wishlist appropriately
		if (isset($configArray['EContent']['hasProtectedEContent'])){
			$interface->assign('hasProtectedEContent', $configArray['EContent']['hasProtectedEContent']);
		}else{
			$interface->assign('hasProtectedEContent', false);
		}

		//This code is also in Search/History since that page displays in the My Account menu as well.
		//It is also in MyList.php and Admin.php
		if ($user !== false){
			$interface->assign('user', $user);

			//Figure out if we should show a link to classic opac to pay holds.
			$ecommerceLink = $configArray['Site']['ecommerceLink'];
			$homeLibrary = Library::getLibraryForLocation($user->homeLocationId);
			if (strlen($ecommerceLink) > 0 && isset($homeLibrary) && $homeLibrary->showEcommerceLink == 1){
				$interface->assign('showEcommerceLink', true);
				$interface->assign('minimumFineAmount', $homeLibrary->minimumFineAmount);
				if ($homeLibrary->payFinesLink == 'default'){
					$interface->assign('ecommerceLink', $ecommerceLink);
				}else{
					$interface->assign('ecommerceLink', $homeLibrary->payFinesLink);
				}
				$interface->assign('payFinesLinkText', $homeLibrary->payFinesLinkText);
			}else{
				$interface->assign('showEcommerceLink', false);
				$interface->assign('minimumFineAmount', 0);
			}
		}
	}

	/**
	 * @param $mainContentTemplate  Name of the SMARTY template file for the main content of the Account Page
	 * @param string $pageTitle     What to display is the html title tag
	 * @param bool|true $sidebar    enables the account sidebar on the page to be displayed
	 */
	function display($mainContentTemplate, $pageTitle= 'My Account', $sidebar=true) {
		global $interface;
		if ($sidebar) $interface->assign('sidebar', 'MyAccount/account-sidebar.tpl');
		$interface->setTemplate($mainContentTemplate);
		$interface->setPageTitle(translate($pageTitle));
		$interface->display('layout.tpl');
	}
}
