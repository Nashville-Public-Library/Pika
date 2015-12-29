{strip}
{* Display Title *}
	<div class="col-xs-12">
		<h2 class="notranslate">
			{$recordDriver->getTitle()|removeTrailingPunctuation|escape}
		</h2>
		<div class="row">
			<div class="col-xs-12 col-sm-5 col-md-4 col-lg-3 text-center">
				{if $user->disableCoverArt != 1}
					<div id = "recordcover" class="text-center row">
						<img alt="{translate text='Book Cover'}" class="img-thumbnail" src="{$recordDriver->getBookcoverUrl('medium')}">
					</div>
				{/if}
				{if $showRatings}
					{include file="GroupedWork/title-rating-full.tpl" ratingClass="" showFavorites=0 ratingData=$recordDriver->getRatingData() showNotInterested=false hideReviewButton=true}
				{/if}
			</div>
			<div class="col-xs-12 col-sm-7 col-md-8 col-lg-9">
				{if $recordDriver->getPrimaryAuthor()}
					<div class="row">
						<div class="result-label col-md-3">Author: </div>
						<div class="col-md-9 result-value notranslate">
							<a href="{$path}/Author/Home?author={$recordDriver->getPrimaryAuthor()|escape:"url"}">{$recordDriver->getPrimaryAuthor()|highlight}</a>
						</div>
					</div>
				{/if}

				{if $recordDriver->getSeries()}
					<div class="series row">
						<div class="result-label col-md-3">Series: </div>
						<div class="col-md-9 result-value">
							{assign var=summSeries value=$recordDriver->getSeries()}
							<a href="{$path}/GroupedWork/{$recordDriver->getPermanentId()}/Series">{$summSeries.seriesTitle}</a>{if $summSeries.volume} volume {$summSeries.volume}{/if}
						</div>
					</div>
				{/if}

				{if $error}{* TODO: Does this get used? *}
					<div class="row">
						<div class="alert alert-danger">
							{$error}
						</div>
					</div>
				{/if}

				{assign value=$recordDriver->getRelatedManifestations() var="relatedManifestations"}
				{include file="GroupedWork/relatedManifestations.tpl"}

				<div class="row">
					{include file='GroupedWork/result-tools-horizontal.tpl' summId=$recordDriver->getPermanentId() summShortId=$recordDriver->getPermanentId() ratingData=$recordDriver->getRatingData() recordUrl=$recordDriver->getLinkUrl() showMoreInfo=false}
				</div>

			</div>
		</div>
	</div>
	{include file=$moreDetailsTemplate}
	<span class="Z3988" title="{$recordDriver->getOpenURL()|escape}" style="display:none">&nbsp;</span>
{/strip}

<script type="text/javascript">
	{literal}$(function(){{/literal}
		VuFind.GroupedWork.loadEnrichmentInfo('{$recordDriver->getPermanentId()|escape:"url"}');
		VuFind.GroupedWork.loadReviewInfo('{$recordDriver->getPermanentId()|escape:"url"}');
		{if $enablePospectorIntegration == 1}
		VuFind.Prospector.loadRelatedProspectorTitles('{$recordDriver->getPermanentId()|escape:"url"}');
		{/if}
	{literal}});{/literal}
</script>