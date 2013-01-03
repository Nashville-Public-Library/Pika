{strip}
<script type="text/javascript" src="/js/highcharts/highcharts.js"></script>
<script type="text/javascript" src="/js/analyticReports.js"></script>

<div id="page-content" class="content">
	{include file="Report/reportSwitcher.tpl"}
	{include file="Report/analyticsFilters.tpl"}
	<div id="pageViewsByModuleContainer" class="reportContainer">
		<div id="pageViewsByModuleChart" class="dashboardChart">
		</div>
		<div class="detailedReportLink"><a href="/Report/DetailedReport?source=pageViewsByModule{if $filterString}&amp;{$filterString|replace:"&":"&amp;"}{/if}">Detailed Report</a></div>
	</div>

	<div id="slowestPageViewsContainer" class="reportContainer2">
		<h2>Slowest Average Pageviews</h2>
		<table id="reportTable">
			<thead>
				<tr><th>Module</th><th>Action</th><th>Method</th><th>Speed</th><th>Num Page Views</th></tr>
			</thead>
			<tbody>
				{foreach from=$slowPages item=pageView}
					<tr><td>{$pageView.module}</td><td>{$pageView.action}</td><td>{$pageView.method}</td><td>{$pageView.loadTime}</td><td>{$pageView.numViews}</td></tr>
				{/foreach}
			</tbody>
		</table>
	</div>

	<div id="pageViewsByThemeContainer" class="reportContainer">
		<div id="pageViewsByThemeChart" class="dashboardChart">
		</div>
		<div class="detailedReportLink"><a href="/Report/DetailedReport?source=pageViewsByTheme{if $filterString}&amp;{$filterString|replace:"&":"&amp;"}{/if}">Detailed Report</a></div>
	</div>
	
	<div id="pageViewsByDeviceContainer" class="reportContainer">
		<div id="pageViewsByDeviceChart" class="dashboardChart">
		</div>
		<div class="detailedReportLink"><a href="/Report/DetailedReport?source=pageViewsByDevice{if $filterString}&amp;{$filterString|replace:"&":"&amp;"}{/if}">Detailed Report</a></div>
	</div>
	
	<div id="pageViewsByHomeLocationContainer" class="reportContainer">
		<div id="pageViewsByHomeLocationChart" class="dashboardChart">
		</div>
		<div class="detailedReportLink"><a href="/Report/DetailedReport?source=pageViewsByHomeLocation{if $filterString}&amp;{$filterString|replace:"&":"&amp;"}{/if}">Detailed Report</a></div>
	</div>
	
	<div id="pageViewsByPhysicalLocationContainer" class="reportContainer">
		<div id="pageViewsByPhysicalLocationChart" class="dashboardChart">
		</div>
		<div class="detailedReportLink"><a href="/Report/DetailedReport?source=pageViewsByPhysicalLocation{if $filterString}&amp;{$filterString|replace:"&":"&amp;"}{/if}">Detailed Report</a></div>
	</div>
</div>
<div class="clearer"></div>
{/strip}
{* Setup charts for rendering*}
<script type="text/javascript">
{literal}

$(document).ready(function() {
	setupPageViewsByModuleChart();
	setupPageViewsByThemeChart();
	setupPageViewsByDeviceChart();
	setupPageViewsByHomeLocationChart();
	setupPageViewsByPhysicalLocationChart();
});
{/literal}
</script>