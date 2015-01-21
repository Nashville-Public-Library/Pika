VuFind.Responsive = (function(){
	$(document).ready(function(){
		$(window).resize(function(){
			VuFind.Responsive.adjustLayout();
		});
		$(window).trigger('resize');
	});

	try{
		var mediaQueryList = window.matchMedia('print');
		mediaQueryList.addListener(function(mql) {
			VuFind.Responsive.isPrint = mql.matches;
			VuFind.Responsive.adjustLayout();
			//console.log("The site is now print? " + VuFind.Responsive.isPrint);
		});
	}catch(err){
		//For now, just ignore this error.
	}

	window.onbeforeprint = function() {
		VuFind.Responsive.isPrint = true;
		VuFind.Responsive.adjustLayout();
	};


	return {
		adjustLayout: function(){
			// get resolution
			var resolution = document.documentElement.clientWidth;

			var mainContentElement = $("#main-content-with-sidebar");
			var xsContentInsertionPointElement = $("#xs-main-content-insertion-point");
			var mainContent;
			if (resolution < 768 && !VuFind.Responsive.isPrint) {
				// @screen-sm-min screen resolution set in \vufind\web\interface\themes\responsive\css\bootstrap\less\variables.less

				//move content from main-content-with-sidebar to xs-main-content-insertion-point
				mainContent = mainContentElement.html();
				if (mainContent && mainContent.length){
					xsContentInsertionPointElement.html(mainContent);
					mainContentElement.html("");
					VuFind.initCarousels();
				}
			}else{
				//Sm or better resolution
				mainContent = xsContentInsertionPointElement.html();
				if (mainContent && mainContent.length){
					mainContentElement.html(mainContent);
					xsContentInsertionPointElement.html("");
					VuFind.initCarousels();
				}
			}

		}
	};
}(VuFind.Responsive || {}));