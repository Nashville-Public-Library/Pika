/**
 * Created by mark on 12/10/2015.
 */
VuFind.Archive = (function(){
	return {
		archive_map: null,
		archive_info_window: null,
		curPage: 1,
		markers: [],
		sort: 'title',

		initializeOpenSeadragon: function(viewer){
			viewer.addHandler("open", this.update_clip);
			viewer.addHandler("animationfinish", this.update_clip);
		},

		getMoreMapResults: function(exhibitPid, placePid){
			this.curPage = this.curPage +1;
			var url = Globals.path + "/Archive/AJAX?method=getRelatedObjectsForMappedCollection&collectionId=" + exhibitPid + "&placeId=" + placePid + "&page=" + this.curPage + "&sort=" + this.sort;
			$("input[name=dateFilter]:checked").each(function(){
				url = url + "&dateFilter[]="+$(this).val();
			});
			url = url + "&reloadHeader=0";

			$.getJSON(url, function(data){
				if (data.success){
					$("#nextInsertPoint").replaceWith(data.relatedObjects);
				}
			});
		},

		handleMapClick: function(markerIndex, exhibitPid, placePid, label){
			$("#related-objects-for-exhibit").html('Loading.');
			this.archive_info_window.setContent(label);
			this.archive_info_window.open(this.archive_map, this.markers[markerIndex]);
			$.getJSON(Globals.path + "/Archive/AJAX?method=getRelatedObjectsForMappedCollection&collectionId=" + exhibitPid + "&placeId=" + placePid, function(data){
				if (data.success){
					$("#related-objects-for-exhibit").html(data.relatedObjects);
				}
			});
			var stateObj = {
				marker: markerIndex,
				exhibitPid: exhibitPid,
				placePid: placePid,
				label: label,
				page: "MapExhibit"
			};
			var newUrl = VuFind.buildUrl(document.location.origin + document.location.pathname, 'placePid', placePid);
			//Push the new url, but only if we aren't going back where we just were.
			if (document.location.href != newUrl){
				history.pushState(stateObj, label, newUrl);
			}
		},

		reloadMapResults: function(exhibitPid, placePid, reloadHeader){
			this.curPage = 1;
			var url = Globals.path + "/Archive/AJAX?method=getRelatedObjectsForMappedCollection&collectionId=" + exhibitPid + "&placeId=" + placePid + "&page=" + this.curPage + "&sort=" + this.sort;
			$("input[name=dateFilter]:checked").each(function(){
				url = url + "&dateFilter[]="+$(this).val();
			});
			url = url + "&reloadHeader=" + reloadHeader;

			$.getJSON(url, function(data){
				if (data.success){
					if (reloadHeader){
						$("#related-objects-for-exhibit").html(data.relatedObjects);
					}else{
						$("#results").html(data.relatedObjects);
					}

				}
			});
		},

		loadExploreMore: function(pid){
			$.getJSON(Globals.path + "/Archive/AJAX?id=" + encodeURI(pid) + "&method=getExploreMoreContent", function(data){
				if (data.success){
					$("#explore-more-body").html(data.exploreMore);
				}
			});
		},

		showObjectInPopup: function(pid){
			var url = Globals.path + "/Archive/AJAX?id=" + encodeURI(pid) + "&method=getObjectInfo";
			VuFind.loadingMessage();
			$.getJSON(url, function(data){
				VuFind.showMessageWithButtons(data.title, data.modalBody, data.modalButtons);
			}).fail(VuFind.ajaxFail);
			return false;
		},

		/**
		 * All this is doing is updatign a URL so the patron can download a clipped portion of the image
		 * not needed for our basic implementation
		 *
		 * @param viewer
		 */
		update_clip: function(viewer) {
			var fitWithinBoundingBox = function(d, max) {
				if (d.width/d.height > max.x/max.y) {
					return new OpenSeadragon.Point(max.x, parseInt(d.height * max.x/d.width));
				} else {
					return new OpenSeadragon.Point(parseInt(d.width * max.y/d.height),max.y);
				}
			}
			var getDisplayRegion = function(viewer, source) {
				// Determine portion of scaled image that is being displayed.
				var box = new OpenSeadragon.Rect(0, 0, source.x, source.y);
				var container = viewer.viewport.getContainerSize();
				var bounds = viewer.viewport.getBounds();
				// If image is offset to the left.
				if (bounds.x > 0){
					box.x = box.x - viewer.viewport.pixelFromPoint(new OpenSeadragon.Point(0,0)).x;
				}
				// If full image doesn't fit.
				if (box.x + source.x > container.x) {
					box.width = container.x - viewer.viewport.pixelFromPoint(new OpenSeadragon.Point(0,0)).x;
					if (box.width > container.x) {
						box.width = container.x;
					}
				}
				// If image is offset up.
				if (bounds.y > 0) {
					box.y = box.y - viewer.viewport.pixelFromPoint(new OpenSeadragon.Point(0,0)).y;
				}
				// If full image doesn't fit.
				if (box.y + source.y > container.y) {
					box.height = container.y - viewer.viewport.pixelFromPoint(new OpenSeadragon.Point(0,0)).y;
					if (box.height > container.y) {
						box.height = container.y;
					}
				}
				return box;
			}
			var source = viewer.source;
			var zoom = viewer.viewport.getZoom();
			var size = new OpenSeadragon.Rect(0, 0, source.dimensions.x, source.dimensions.y);
			var container = viewer.viewport.getContainerSize();
			var fit_source = fitWithinBoundingBox(size, container);
			var total_zoom = fit_source.x/source.dimensions.x;
			var container_zoom = fit_source.x/container.x;
			var level = (zoom * total_zoom) / container_zoom;
			var box = getDisplayRegion(viewer, new OpenSeadragon.Point(parseInt(source.dimensions.x*level), parseInt(source.dimensions.y*level)));
			var scaled_box = new OpenSeadragon.Rect(parseInt(box.x/level), parseInt(box.y/level), parseInt(box.width/level), parseInt(box.height/level));
			var params = {
				'url_ver': 'Z39.88-2004',
				'rft_id': source.imageID,
				'svc_id': 'info:lanl-repo/svc/getRegion',
				'svc_val_fmt': 'info:ofi/fmt:kev:mtx:jpeg2000',
				'svc.format': 'image/jpeg',
				'svc.region': scaled_box.y + ',' + scaled_box.x + ',' + (scaled_box.getBottomRight().y - scaled_box.y) + ',' + (scaled_box.getBottomRight().x - scaled_box.x),
			};
			var dimensions = (zoom <= 1) ? source.dimensions.x + ',' + source.dimensions.y : container.x + ',' + container.y;
			jQuery("#clip").attr('href',  'https://islandora.marmot.org/islandora/object/' + settings.islandoraOpenSeadragon.pid + '/print?' + jQuery.param({
						'clip': source.baseURL + '?' + jQuery.param(params),
						'dimensions': dimensions,
					}));
		}
	}

}(VuFind.Archive || {}));