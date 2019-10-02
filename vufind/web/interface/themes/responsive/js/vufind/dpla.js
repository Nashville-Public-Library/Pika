/**
 * Created by mark on 2/9/15.
 */
VuFind.DPLA = (function(){
	return {
		getDPLAResults: function(searchTerm){
			var url = Globals.path + "/Search/AJAX",
					params = {
						'method' : 'getDplaResults',
						searchTerm : searchTerm,
					};
			$.getJSON(url, params, function(data) {
				var searchResults = data.formattedResults;
				if (searchResults && searchResults.length > 0) {
					$("#dplaSearchResultsPlaceholder").html(searchResults);
				}
			});
		}
	}
}(VuFind.DPLA || {}));