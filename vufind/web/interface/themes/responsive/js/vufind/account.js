/**
 * Created by mark on 1/14/14.
 */
VuFind.Account = (function(){

	return {
		ajaxCallback: null,
		closeModalOnAjaxSuccess: false,

		/**
		 * Creates a new list in the system for the active user.
		 *
		 * Called from list-form.tpl
		 * @returns {boolean}
		 */
		addList: function () {
			var form = $("#addListForm");
			var isPublic = form.find("#public").prop("checked");
			var recordId = form.find("input[name=recordId]").val();
			var source = form.find("input[name=source]").val();
			var title = form.find("input[name=title]").val();
			var desc = $("#listDesc").val();

			var url = Globals.path + "/MyResearch/AJAX";
			var params = "method=AddList&" +
					"title=" + encodeURIComponent(title) + "&" +
					"public=" + isPublic + "&" +
					"desc=" + encodeURIComponent(desc) + "&" +
					"recordId=" + encodeURIComponent(recordId) ;

			$.ajax({
				url: url + '?' + params,
				dataType: "json",
				success: function (data) {
					if (data.result) {
						VuFind.showMessage("Added Successfully", data.message);
					} else {
						VuFind.showMessage("Error", data.message);
					}
				},
				error: function () {
					VuFind.showMessage("Error creating list", "There was an unexpected error creating your list");
				}
			});

			return false;
		},

		/**
		 * Do an ajax process, but only if the user is logged in.
		 * If the user is not logged in, force them to login and then do the process.
		 * Can also be called without the ajax callback to just login and not go anywhere
		 *
		 * @param trigger
		 * @param ajaxCallback
		 * @param closeModalOnAjaxSuccess
		 * @returns {boolean}
		 */
		ajaxLogin: function (trigger, ajaxCallback, closeModalOnAjaxSuccess) {
			if (Globals.loggedIn) {
				if (ajaxCallback != undefined && typeof(ajaxCallback) === "function") {
					ajaxCallback();
				} else if (VuFind.Account.ajaxCallback != null && typeof(VuFind.Account.ajaxCallback) === "function") {
					VuFind.Account.ajaxCallback();
					VuFind.Account.ajaxCallback = null;
				}
			} else {
				var multistep = false;
				if (ajaxCallback != undefined && typeof(ajaxCallback) === "function") {
					multistep = true;
				}
				VuFind.Account.ajaxCallback = ajaxCallback;
				VuFind.Account.closeModalOnAjaxSuccess = closeModalOnAjaxSuccess;
				if (trigger != undefined && trigger != null) {
					var dialogTitle = trigger.attr("title") ? trigger.attr("title") : trigger.data("title");
				}
				var dialogDestination = Globals.path + '/MyAccount/AJAX?method=LoginForm';
				if (multistep){
					dialogDestination += "&multistep=true";
				}
				var modalDialog = $("#modalDialog");
				$('.modal-body').html("Loading...");
				var modalBody = $(".modal-content");
				modalBody.load(dialogDestination);
				$(".modal-title").text(dialogTitle);
				modalDialog.modal("show");
			}
			return false;
		},

		followLinkIfLoggedIn: function (trigger, linkDestination) {
			if (trigger == undefined) {
				alert("You must provide the trigger to follow a link after logging in.");
			}
			var jqTrigger = $(trigger);
			if (linkDestination == undefined) {
				linkDestination = jqTrigger.attr("href");
			}
			this.ajaxLogin(jqTrigger, function () {
				document.location = linkDestination;
			}, true);
			return false;
		},

		preProcessLogin: function (){
			var username = $("#username").val();
			var password = $("#password").val();
			var rememberMeCtl = $("#rememberMe");
			var rememberMe = rememberMeCtl.prop('checked');
			var loginErrorElem = $('#loginError');
			if (!username || !password) {
				loginErrorElem.text("Please enter both your name and library card number");
				loginErrorElem.show();
				return false;
			}
			if (rememberMe){
				localStorage.lastUserName = username;
				localStorage.lastPwd = password;
			}else{
				localStorage.lastUserName = "";
				localStorage.lastPwd = "";
			}
			return true;
		},

		processAjaxLogin: function (ajaxCallback) {
			var username = $("#username").val();
			var password = $("#password").val();
			var rememberMeCtl = $("#rememberMe");
			var rememberMe = rememberMeCtl.prop('checked');
			var loginErrorElem = $('#loginError');
			if (!username || !password) {
				loginErrorElem.text("Please enter both your name and library card number");
				loginErrorElem.show();
				return false;
			}
			if (rememberMe){
				localStorage.lastUserName = username;
				localStorage.lastPwd = password;
			}else{
				localStorage.lastUserName = "";
				localStorage.lastPwd = "";
			}
			loginErrorElem.hide();
			var url = Globals.path + "/AJAX/JSON?method=loginUser";
			$.ajax({url: url,
				data: {username: username, password: password, rememberMe: rememberMe},
				success: function (response) {
					if (response.result.success == true) {
						// Hide "log in" options and show "log out" options:
						$('.loginOptions, #loginOptions').hide();
						$('.logoutOptions, #logoutOptions').show();
						//$('#loginOptions').hide();
						//$('#logoutOptions').show();

						var name = response.result.name.trim();
						$('#header-container #myAccountNameLink').html(name);
						name = 'Logged In As '+ name.slice(0, name.lastIndexOf(' ')+2)+'.';
						$('#side-bar #myAccountNameLink').html(name);

						if (VuFind.Account.closeModalOnAjaxSuccess) {
							VuFind.closeLightbox();
						}

						Globals.loggedIn = true;
						if (ajaxCallback != undefined && typeof(ajaxCallback) === "function") {
							ajaxCallback();
						} else if (VuFind.Account.ajaxCallback != undefined && typeof(VuFind.Account.ajaxCallback) === "function") {
							VuFind.Account.ajaxCallback();
							VuFind.Account.ajaxCallback = null;
						}
					} else {
						loginErrorElem.text(response.result.message);
						loginErrorElem.show();
					}
				},
				error: function () {
					loginErrorElem.text("There was an error processing your login, please try again.");
					loginErrorElem.show();
				},
				dataType: 'json',
				type: 'post'
			});

			return false;
		},

		removeTag: function(tag){
			if (confirm("Are you sure you want to remove the tag \"" + tag + "\" from all titles?")){
				var url = Globals.path + "/MyAccount/AJAX?method=removeTag&tag=" + encodeURI(tag);
				$.getJSON(url, function(data){
					if (data.result == true){
						VuFind.showMessage('Tag Deleted', data.message);
						setTimeout(function(){window.location.reload()}, 3000);
					}else{
						VuFind.showMessage('Tag Not Deleted', data.message);
					}
				});
			}
			return false;
		},

		renewSelectedTitles: function () {
			var selectedTitles = VuFind.getSelectedTitles();
			if (selectedTitles.length == 0) {
				return false;
			}
			$('#renewForm').submit();
			return false;
		},

		ajaxLightbox: function (urlToDisplay, requireLogin) {
			if (requireLogin == undefined) {
				requireLogin = false;
			}
			if (requireLogin && !Globals.loggedIn) {
				VuFind.Account.ajaxLogin(null, function () {
					VuFind.Account.ajaxLightbox(urlToDisplay, requireLogin);
				}, false);
			} else {
				var modalDialog = $("#modalDialog");
				$('#myModalLabel').html("Loading, please wait");
				$('.modal-body').html("...");
				$.getJSON(urlToDisplay, function(data){
					if (data.result){
						data = data.result;
					}
					$('#myModalLabel').html(data.title);
					$('.modal-body').html(data.modalBody);
					$('.modal-buttons').html(data.modalButtons);
				});
				modalDialog.load( );
				modalDialog.modal('show');
			}
			return false;
		},

		cancelPendingHold: function(holdIdToCancel, recordId){
			if (!confirm("Are you sure you want to cancel this hold?")){
				return false;
			}
			var url = Globals.path + '/MyAccount/Holds?multiAction=cancelSelected&waitingholdselected[]=' + holdIdToCancel;
			url += '&recordId[' + holdIdToCancel + ']=' + recordId;
			var queryParams = VuFind.getQuerystringParameters();
			if ($.inArray('section', queryParams) && queryParams['section'] != 'undefined'){
				url += '&section=' + queryParams['section'];
			}
			window.location = url;
			return false;
		},

		cancelAvailableHold: function(holdIdToCancel, recordId){
			if (!confirm("Are you sure you want to cancel this hold?")){
				return false;
			}
			var url = Globals.path + '/MyAccount/Holds?multiAction=cancelSelected&availableholdselected[]=' + holdIdToCancel;
			url += '&recordId[' + holdIdToCancel + ']=' + recordId;
			var queryParams = VuFind.getQuerystringParameters();
			if ($.inArray('section', queryParams) && queryParams['section'] != 'undefined'){
				url += '&section=' + queryParams['section'];
			}
			window.location = url;
			return false;
		},

		cancelSelectedHolds: function(){
			var numHolds = $("input.titleSelect:checked ").length;
			if (numHolds == 0){
				alert('Please select one or more titles to cancel.');
				return false;
			} else if (!confirm('Cancel '+numHolds +' selected hold'+(numHolds > 1?'s':'') +'?')) {
					return false;
			}
			var selectedTitles = this.getSelectedTitles(false),
					url = Globals.path + '/MyAccount/Holds?multiAction=cancelSelected&' + selectedTitles,
					queryParams = VuFind.getQuerystringParameters();
			if ($.inArray('section', queryParams) && queryParams['section'] != 'undefined'){
				url += '&section=' + queryParams['section'];
			}
			window.location = url;
			return false;

		},

		/* update the sort parameter and redirect the user back to the same page */
		changeAccountSort: function (newSort){
			// Get the current url
			var currentLocation = window.location.href;
			// Check to see if we already have a sort parameter. .
			if (currentLocation.match(/(accountSort=[^&]*)/)) {
				// Replace the existing sort with the new sort parameter
				currentLocation = currentLocation.replace(/accountSort=[^&]*/, 'accountSort=' + newSort);
			} else {
				// Add the new sort parameter
				if (currentLocation.match(/\?/)) {
					currentLocation += "&accountSort=" + newSort;
				}else{
					currentLocation += "?accountSort=" + newSort;
				}
			}
			// Redirect back to this page.
			window.location.href = currentLocation;
		},

		changeHoldPickupLocation: function (holdId){
			if (Globals.loggedIn){
				var modalDialog = $("#modalDialog");
				//$(".modal-body").html($('#userreview' + id).html());
				$.getJSON(Globals.path + "/MyAccount/AJAX?method=getChangeHoldLocationForm&holdId=" + holdId, function(data){
					$('#myModalLabel').html(data.title);
					$('.modal-body').html(data.modalBody);
					$('.modal-buttons').html(data.modalButtons);
				});
				modalDialog.load( );
				modalDialog.modal('show');
			}else{
				VuFind.Account.ajaxLogin(null, function (){
					return VuFind.Account.changeHoldPickupLocation(holdId);
				}, false);
			}
			return false;
		},

		deleteSearch: function(searchId){
			if (!Globals.loggedIn){
				VuFind.Account.ajaxLogin(null, function () {
					VuFind.Searches.saveSearch(searchId);
				}, false);
			}else{
				var url = Globals.path + "/MyAccount/AJAX";
				var params = "method=deleteSearch&searchId=" + encodeURIComponent(searchId);
				$.getJSON(url + '?' + params,
						function(data) {
							if (data.result) {
								VuFind.showMessage("Success", data.message);
							} else {
								VuFind.showMessage("Error", data.message);
							}
						}
				);
			}
			return false;
		},

		doChangeHoldLocation: function(){
			var holdId = $('#holdId').val();
			var newLocation = $('#newPickupLocation').val();
			var url = Globals.path + "/MyAccount/AJAX?method=changeHoldLocation&holdId=" + encodeURIComponent(holdId) + "&newLocation=" + encodeURIComponent(newLocation);
			$.getJSON(url,
					function(data) {
						if (data.result) {
							VuFind.showMessage("Success", data.message, true, true);
						} else {
							VuFind.showMessage("Error", data.message);
						}
					}
			);
		},

		freezeHold: function(holdId, promptForReactivationDate, caller){
			if (promptForReactivationDate){
				//Prompt the user for the date they want to reactivate the hold
				var modalDialog = $("#modalDialog");
				//$(".modal-body").html($('#userreview' + id).html());
				$.getJSON(Globals.path + "/MyAccount/AJAX?method=getReactivationDateForm&holdId=" + holdId, function(data){
					$('#myModalLabel').html(data.title);
					$('.modal-body').html(data.modalBody);
					$('.modal-buttons').html(data.modalButtons);
				});
				modalDialog.load( );
				modalDialog.modal('show');
			}else{
				popUpBoxTitle = $(caller).text() || "Freezing Hold"; // freezing terminology can be customized, so grab text from click button: caller
				VuFind.showMessage(popUpBoxTitle, "Updating your hold.  This may take a minute.");
				var url = Globals.path + '/MyAccount/AJAX?method=freezeHold&holdId=' + holdId;
				$.getJSON(url, function(data){
					if (data.result) {
						VuFind.showMessage("Success", data.message, true, true);
					} else {
						VuFind.showMessage("Error", data.message);
					}
				});
			}
		},

/* not part of implemented action as of 1-26-2015. plb
		doFreezeHoldWithReactivationDate: function(){
			var holdId = $("#holdId").val();
			var reactivationDate = $("#reactivationDate").val();
			var url = Globals.path + '/MyAccount/AJAX?method=freezeHold&holdId=' + holdId + '&reactivationDate=' + reactivationDate;
			VuFind.showMessage("Freezing Hold", "Freezing your hold.  This may take a minute.");
			$.getJSON(url, function(data){
				if (data.result) {
					VuFind.showMessage("Success", data.message, true, true);
				} else {
					VuFind.showMessage("Error", data.message);
				}
			});
		},
*/
		freezeSelectedHolds: function (){
			var selectedTitles = this.getSelectedTitles();
			if (selectedTitles.length == 0){
				return false;
			}
			var suspendDate = '';
			//Check to see whether or not we are using a suspend date.
			var suspendDateTop = $('#suspendDateTop');
			var url = '';
			var queryParams = '';
			if (suspendDateTop.length){
				if (suspendDateTop.val().length > 0){
					suspendDate = suspendDateTop.val();
				}else{
					suspendDate = $('#suspendDateBottom').val();
				}

				if (suspendDate.length == 0){
					alert("Please select the date when the hold should be reactivated.");
					return false;
				}
				url = Globals.path + '/MyAccount/Holds?multiAction=freezeSelected&' + selectedTitles + '&suspendDate=' + suspendDate;
				queryParams = VuFind.getQuerystringParameters();
				if ($.inArray('section', queryParams)){
					url += '&section=' + queryParams['section'];
				}
				window.location = url;
			}else{
				url = Globals.path + '/MyAccount/Holds?multiAction=freezeSelected&' + selectedTitles + '&suspendDate=' + suspendDate;
				queryParams = VuFind.getQuerystringParameters();
				if ($.inArray('section', queryParams)){
					url += '&section=' + queryParams['section'];
				}
				window.location = url;
			}
			return false;
		},

		getSelectedTitles: function(promptForSelectAll){
			if (promptForSelectAll == undefined){
				promptForSelectAll = true;
			}
			var selectedTitles = $("input.titleSelect:checked ").map(function() {
				return $(this).attr('name') + "=" + $(this).val();
			}).get().join("&");
			if (selectedTitles.length == 0 && promptForSelectAll){
				var ret = confirm('You have not selected any items, process all items?');
				if (ret == true){
					var titleSelect = $("input.titleSelect");
					titleSelect.attr('checked', 'checked');
					selectedTitles = titleSelect.map(function() {
						return $(this).attr('name') + "=" + $(this).val();
					}).get().join("&");
				}
			}
			return selectedTitles;
		},

		saveSearch: function(searchId){
			if (!Globals.loggedIn){
				VuFind.Account.ajaxLogin(null, function () {
					VuFind.Searches.saveSearch(searchId);
				}, false);
			}else{
				var url = Globals.path + "/MyAccount/AJAX";
				var params = "method=saveSearch&searchId=" + encodeURIComponent(searchId);
				$.getJSON(url + '?' + params,
						function(data) {
							if (data.result) {
								VuFind.showMessage("Success", data.message);
							} else {
								VuFind.showMessage("Error", data.message);
							}
						}
				);
			}
			return false;
		},

		showCreateListForm: function(id){
			if (Globals.loggedIn){
				var modalDialog = $("#modalDialog");
				//$(".modal-body").html($('#userreview' + id).html());
				var url = Globals.path + "/MyResearch/AJAX?method=getCreateListForm";
				if (id != undefined){
					url += '&recordId=' + encodeURIComponent(id);
				}
				$.getJSON(url, function(data){
					$('#myModalLabel').html(data.title);
					$('.modal-body').html(data.modalBody);
					$('.modal-buttons').html(data.modalButtons);
				});
				modalDialog.load( );
				modalDialog.modal('show');
			}else{
				VuFind.Account.ajaxLogin($trigger, function (){
					return VuFind.GroupedWork.showEmailForm(trigger, id);
				}, false);
			}
			return false;
		},

		thawHold: function(holdId, caller){
			$popUpBoxTitle = $(caller).text() || "Thawing Hold";  // freezing terminology can be customized, so grab text from click button: caller
			VuFind.showMessage($popUpBoxTitle, "Updating your hold.  This may take a minute.");
			var url = Globals.path + '/MyAccount/AJAX?method=thawHold&holdId=' + holdId;
			$.getJSON(url, function(data){
				if (data.result) {
					VuFind.showMessage("Success", data.message, true, true);
				} else {
					VuFind.showMessage("Error", data.message);
				}
			});
		}

	};
}(VuFind.Account || {}));