VuFind.Prospector = (function(){
	return {
		getProspectorResults: function(prospectorNumTitlesToLoad, prospectorSavedSearchId){
			var url = Globals.path + "/Search/AJAX",
					params = {
						'method': 'getProspectorResults',
						prospectorNumTitlesToLoad: prospectorNumTitlesToLoad,
						prospectorSavedSearchId: prospectorSavedSearchId,
					};
			$.get(url, params, function (data) {
				$("#prospectorSearchResultsPlaceholder").html(data);
			});
		},

		loadRelatedProspectorTitles: function (id) {
			var url = Globals.path + "/GroupedWork/" + encodeURIComponent(id) + "/AJAX",
					params = {'method': 'getProspectorInfo'};
			$.getJSON(url, params, function (data) {
				if (data.numTitles === 0) {
					$("#prospectorPanel").hide();
				}else{
					$("#inProspectorPlaceholder").html(data.formattedData);
				}
			});
		},

		removeBlankThumbnail: function(imgElem, elemToHide, isForceRemove) {
			var $img = $(imgElem);
			//when the content providers cannot find a bookjacket, they return a 1x1 pixel
			//remove the wrapping div, for consistent spacing with other results
			if ($img.height() == 1 && $img.width() == 1 || isForceRemove) {
				$(elemToHide).remove();
			}
		}
	}
}(VuFind.Prospector || {}));