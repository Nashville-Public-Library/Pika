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
require_once ROOT_DIR . '/sys/Mailer.php';
require_once ROOT_DIR . '/sys/LocalEnrichment/UserList.php';
require_once ROOT_DIR . '/services/MyResearch/lib/FavoriteHandler.php';

class EmailList extends Action {
	function launch() {
		global $interface;

		if (isset($_POST['from'])) {
			$result = $this->sendEmail($_POST['to'], $_POST['from'], $_POST['message']);

			if (!PEAR_Singleton::isError($result)) {
				require_once 'MyList.php';
				header("Location:/MyAccount/MyList/" . $_REQUEST['listId']);
				die();
			} else {
				$interface->assign('message', $result->getMessage());
			}
		}else{
			// Display Page
			$interface->assign('listId', strip_tags($_REQUEST['id']));
			$formDefinition = array(
					'title' => 'Email a list',
					'modalBody' => $interface->fetch('MyAccount/emailListPopup.tpl'),
					'modalButtons' => "<input type='submit' name='submit' value='Send' class='btn btn-primary' onclick='$(\"#emailListForm\").submit()'/>"
			);
			echo json_encode($formDefinition);
		}
	}

	function sendEmail($to, $from, $message) {
		global $interface;
		global $user;

		//Load the list
		$list = new UserList();
		$list->id = $_REQUEST['listId'];
		if ($list->find(true)){
			// Build Favorites List
			$titles = $list->getListTitles();
			$interface->assign('listEntries', $titles);

			// Load the User object for the owner of the list (if necessary):
			if ($user && $user->id == $list->user_id || $list->public == 1) {
				//The user can access the list
				$favoriteHandler = new FavoriteHandler($titles, $user, $list->id, false);
				$titleDetails = $favoriteHandler->getTitles(count($titles));
				$interface->assign('titles', $titleDetails);
				$interface->assign('list', $list);
			} else {
				$interface->assign('error', 'You do not have access to this list.');
			}
		}else{
			$interface->assign('error', 'Unable to read list');
		}

		//$interface->assign('from', $from);
		// not used in my-list.tpl  plb 10-7-2014

		if (strpos($message, 'http') === false && strpos($message, 'mailto') === false && $message == strip_tags($message)){
			$interface->assign('message', $message);
			$body = $interface->fetch('Emails/my-list.tpl');
			
			$mail = new VuFindMailer();
			$subject = $list->title;
			return $mail->send($to, $from, $subject, $body);
		}else{
			return false;
		}
	}
}
?>