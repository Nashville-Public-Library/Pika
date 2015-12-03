{strip}
	<div data-role="content">
		{if $user->cat_username}
			{if $profile->web_note}
				<div id="web_note" class="text-info text-center alert alert-warning"><strong>{$profile->web_note}</strong></div>
			{/if}

			<h3>{translate text='Account Summary'}</h3>
			<div>
				You currently have:
				<ul>
					<li><strong>{$profile->getNumCheckedOutTotal()}</strong> titles <a href="{$path}/MyAccount/CheckedOut">checked out</a></li>
					<li><strong>{$profile->getNumHoldsTotal()}</strong> titles on <a href="{$path}/MyAccount/Holds">hold</a></li>
				</ul>
				{* TODO: Show an alert if any titles are expired or are going to expire *}
				{* TODO: Show an alert if any titles ready for pickup *}
			</div>

			{if $showRatings}
				<h3>{translate text='Recommended for you'}</h3>
				{if !$hasRatings}
					<p>
						You have not rated any titles.
						If you rate titles, we can provide you with suggestions for titles you might like to read.
						Suggestions are based on titles you like (rated 4 or 5 stars) and information within the catalog.
						Library staff does not have access to your suggestions.
					</p>
				{else}
					<p>Based on the titles you have <a href="{$path}/MyAccount/MyRatings">rated</a>, we have <a href="{$path}/MyAccount/SuggestedTitles">suggestions for you</a>.  To improve your suggestions keep rating more titles.</p>
					{foreach from=$suggestions item=suggestion name=recordLoop}
						<div class="result {if ($smarty.foreach.recordLoop.iteration % 2) == 0}alt{/if} record{$smarty.foreach.recordLoop.iteration}">
							{$suggestion}
						</div>
					{/foreach}
				{/if}
			{/if}
		{else}
			You must login to view this information. Click <a href="{$path}/MyAccount/Login">here</a> to login.
		{/if}
	</div>
{/strip}