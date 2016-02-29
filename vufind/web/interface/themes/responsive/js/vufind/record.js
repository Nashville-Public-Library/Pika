VuFind.Record = (function(){
	return {
		showPlaceHold: function(module, id){
			if (Globals.loggedIn){
				var source;
				var volume = null;
				if (id.indexOf(":") > 0){
					var idParts = id.split(":");
					source = idParts[0];
					id = idParts[1];
					if (idParts.length > 2){
						volume = idParts[2];
					}
				}else{
					source = 'ils';
				}
				var url = Globals.path + "/" + module + "/" + id + "/AJAX?method=getPlaceHoldForm&recordSource=" + source;
				if (volume != null){
					url += "&volume=" + volume;
				}
				//VuFind.showMessage('Loading...', 'Loading, please wait.');
				$.getJSON(url, function(data){
					VuFind.showMessageWithButtons(data.title, data.modalBody, data.modalButtons);
				}).fail(VuFind.ajaxFail);
			}else{
				VuFind.Account.ajaxLogin(null, function(){
					VuFind.Record.showPlaceHold(module, id);
				}, false);
			}
			return false;
		},

		showBookMaterial: function(module, id){
			if (Globals.loggedIn){
				VuFind.loadingMessage();
				//var source; // source not used for booking at this time
				if (id.indexOf(":") > 0){
					var idParts = id.split(":", 2);
					//source = idParts[0];
					id = idParts[1];
				//}else{
				//	source = 'ils';
				}
				$.getJSON(Globals.path + "/" + module + "/" + id + "/AJAX?method=getBookMaterialForm", function(data){
					VuFind.showMessageWithButtons(data.title, data.modalBody, data.modalButtons);
				}).fail(VuFind.ajaxFail)
			}else{
				VuFind.Account.ajaxLogin(null, function(){
					VuFind.Record.showBookMaterial(id);
				}, false)
			}
			return false;
		},

		submitBookMaterialForm: function(){
			var params = $('#bookMaterialForm').serialize();
			var module = $('#module').val();
			VuFind.showMessage('Scheduling', 'Processing, please wait.');
			$.getJSON(Globals.path + "/" + module +"/AJAX", params+'&method=bookMaterial', function(data){
				if (data.modalBody) VuFind.showMessageWithButtons(data.title, data.modalBody, data.modalButtons);
					// For errors that can be fixed by the user, the form will be re-displayed
				if (data.success) VuFind.showMessage('Success', data.message/*, true*/);
				else if (data.message) VuFind.showMessage('Error', data.message);
			}).fail(VuFind.ajaxFail);
		},

		submitHoldForm: function(){
			var id = $('#id').val()
					,autoLogOut = $('#autologout').prop('checked')
					,selectedItem = $('#selectedItem')
					,module = $('#module').val()
					,volume = $('#volume')
					,params = {
						'method': 'placeHold'
						,campus: $('#campus').val()
						,selectedUser: $('#user').val()
						,canceldate: $('#canceldate').val()
						,recordSource: $('#recordSource').val()
						,account: $('#account').val()
					};
			if (autoLogOut){
				params['autologout'] = true;
			}
			if (selectedItem.length > 0){
				params['selectedItem'] = selectedItem.val();
			}
			if (volume.length > 0){
				params['volume'] = volume.val();
			}
			$.getJSON(Globals.path + "/" + module +  "/" + id + "/AJAX", params, function(data){
				if (data.success){
					if (data.needsItemLevelHold){
						$('.modal-body').html(data.message);
					}else{
						VuFind.showMessage('Hold Placed Successfully', data.message, false, autoLogOut);
					}
				}else{
					VuFind.showMessage('Hold Failed', data.message, false, autoLogOut);
				}
			}).fail(VuFind.ajaxFail);
		},

		reloadCover: function(module, id){
			var url = Globals.path + '/' +module + '/' + id + '/AJAX?method=reloadCover';
			$.getJSON(url, function (data){
						VuFind.showMessage("Success", data.message, true, true);
						setTimeout("VuFind.closeLightbox();", 3000);
					}
			).fail(VuFind.ajaxFail);
			return false;
		},

		moreContributors: function(){
			document.getElementById('showAdditionalContributorsLink').style.display="none";
			document.getElementById('additionalContributors').style.display="block";
		},

		lessContributors: function(){
			document.getElementById('showAdditionalContributorsLink').style.display="block";
			document.getElementById('additionalContributors').style.display="none";
		}

	};
}(VuFind.Record || {}));