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

require_once ROOT_DIR . '/services/MyAccount/MyAccount.php';

class MyAccount_Profile extends MyAccount
{
	function launch()
	{
		global $configArray;
		global $interface;
		global $user;

		/** @var Library $librarySingleton */
		global $librarySingleton;
		$activeLibrary = $librarySingleton->getActiveLibrary();
		if ($activeLibrary == null){
			$canUpdateContactInfo = true;
			$canUpdateAddress = true;
			$showWorkPhoneInProfile = false;
			$showNoticeTypeInProfile = true;
			$showPickupLocationInProfile = false;
			$treatPrintNoticesAsPhoneNotices = false;
			$allowPinReset = false;
			$showAlternateLibraryOptionsInProfile = true;
		}else{
			$canUpdateContactInfo = ($activeLibrary->allowProfileUpdates == 1);
			$canUpdateAddress = ($activeLibrary->allowPatronAddressUpdates == 1);
			$showWorkPhoneInProfile = ($activeLibrary->showWorkPhoneInProfile == 1);
			$showNoticeTypeInProfile = ($activeLibrary->showNoticeTypeInProfile == 1);
			$treatPrintNoticesAsPhoneNotices = ($activeLibrary->treatPrintNoticesAsPhoneNotices == 1);
			$showPickupLocationInProfile = ($activeLibrary->showPickupLocationInProfile == 1);
			$allowPinReset = ($activeLibrary->allowPinReset == 1);
			$showAlternateLibraryOptionsInProfile = ($activeLibrary->showAlternateLibraryOptionsInProfile == 1);
		}
		if ($showPickupLocationInProfile) { // only grab pickup locations if needed.
			global $locationSingleton;
			//Get the list of pickup branch locations for display in the user interface.
			$locations = $locationSingleton->getPickupBranches($user, $user->homeLocationId);
			$interface->assign('pickupLocations', $locations);
		}

		$interface->assign('canUpdateContactInfo', $canUpdateContactInfo);
		$interface->assign('canUpdateAddress', $canUpdateAddress);
		$interface->assign('showWorkPhoneInProfile', $showWorkPhoneInProfile);
		$interface->assign('showPickupLocationInProfile', $showPickupLocationInProfile);
		$interface->assign('showNoticeTypeInProfile', $showNoticeTypeInProfile);
		$interface->assign('treatPrintNoticesAsPhoneNotices', $treatPrintNoticesAsPhoneNotices);
		$interface->assign('allowPinReset', $allowPinReset);
		$interface->assign('showAlternateLibraryOptions', $showAlternateLibraryOptionsInProfile);


		$ils = $configArray['Catalog']['ils'];
		$interface->assign('showSMSNoticesInProfile', $ils == 'Sierra');
		if ($configArray['Catalog']['offline']){
			$interface->assign('offline', true);
		}else{
			$interface->assign('offline', false);
		}

		if (isset($_POST['updateScope']) && !$configArray['Catalog']['offline']) {
			$updateScope = $_REQUEST['updateScope'];
			if ($updateScope == 'contact'){
				$errors = $this->catalog->updatePatronInfo($canUpdateContactInfo);
				session_start(); // any writes to the session storage also closes session. Happens in updatePatronInfo (for Horizon). plb 4-21-2015
				$_SESSION['profileUpdateErrors'] = $errors;

			}elseif($updateScope == 'catalog'){
				$user->updateCatalogOptions();
			}elseif($updateScope == 'overdrive'){
				// overdrive setting keep changing
			/*	require_once ROOT_DIR . '/Drivers/OverDriveDriverFactory.php';
				$overDriveDriver = OverDriveDriverFactory::getDriver();
				$result = $overDriveDriver->updateLendingOptions();
*/
				$user->updateOverDriveOptions();
			}elseif ($updateScope == 'pin') {
				$errors = $this->catalog->updatePin();
				session_start(); // any writes to the session storage also closes session. possibly happens in updatePin. plb 4-21-2015
				$_SESSION['profileUpdateErrors'] = $errors;
			}

			session_write_close();
			header("Location: " . $configArray['Site']['path'] . '/MyAccount/Profile');
			exit();
		}elseif (!$configArray['Catalog']['offline']){
			$interface->assign('edit', true);
		}else{
			$interface->assign('edit', false);
		}

		/*require_once ROOT_DIR . '/Drivers/OverDriveDriverFactory.php';
		$overDriveDriver = OverDriveDriverFactory::getDriver();
		if ($overDriveDriver->version >= 2){
			$lendingPeriods = $overDriveDriver->getLendingPeriods($user);
			$interface->assign('overDriveLendingOptions', $lendingPeriods);
		}*/
		$interface->assign('overDriveUrl', $configArray['OverDrive']['url']);

		// TODO: bug error messages not surviving reload in session.
		if (isset($_SESSION['profileUpdateErrors'])){
			$interface->assign('profileUpdateErrors', $_SESSION['profileUpdateErrors']);
			unset($_SESSION['profileUpdateErrors']);
		}

		//Get the list of locations for display in the user interface.
		$location = new Location();
		$location->validHoldPickupBranch = 1;
		$location->find();

		$locationList = array();
		while ($location->fetch()) {
			$locationList[$location->locationId] = $location->displayName;
		}
		$interface->assign('locationList', $locationList);

		if ($this->catalog->checkFunction('isUserStaff')){
			$userIsStaff = $this->catalog->isUserStaff();
			$interface->assign('userIsStaff', $userIsStaff);
		}else{
			$interface->assign('userIsStaff', false);
		}

		$interface->assign('sidebar', 'MyAccount/account-sidebar.tpl');
		$interface->setTemplate('profile.tpl');
		$interface->setPageTitle(translate('Account Settings'));
		$interface->display('layout.tpl');
	}

}