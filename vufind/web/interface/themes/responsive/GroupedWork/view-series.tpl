{* Main Listing *}
{if (isset($title)) }
<script type="text/javascript">
	alert("{$title}");
</script>
{/if}
<div class="col-xs-12">
	{if $seriesTitle}
	<h2 class="notranslate">
		{$seriesTitle}
	</h2>
	{/if}
	{if $seriesAuthors}
	<div class="row">
		<div class="result-label col-tn-3">Author: </div>
		<div class="col-tn-9 result-value notranslate">
			{foreach from=$seriesAuthors item=author}
				<span class="sidebarValue">{$author} </span>
			{/foreach}
		</div>
	</div>
	{/if}

	{* Eventually, we will put the series title here*}

	<div id="main-content">
	{* Listing Options *}
	<div id="searchInfo">
		{if $recordCount}
			{translate text="Showing"}
			<b>{$recordStart}</b> - <b>{$recordEnd}</b>
			{translate text='of'} <b>{$recordCount}</b>
		{else}
			<p>Sorry, we could not find series information for this title.</p>
		{/if}
	</div>
	{* End Listing Options *}

	{* Display series information *}
	<div id="seriesTitles">
		{foreach from=$resourceList item=resource name="recordLoop"}
			<div class="result{if ($smarty.foreach.recordLoop.iteration % 2) == 0} alt{/if}">
				{* This is raw HTML -- do not escape it: *}
				{$resource}
			</div>

		{/foreach}
	</div>

	{if $pageLinks.all}<div class="pagination">{$pageLinks.all}</div>{/if}
	</div>
</div>