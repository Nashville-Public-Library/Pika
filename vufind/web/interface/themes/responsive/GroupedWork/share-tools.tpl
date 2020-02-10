{strip}
	{if $showEmailThis == 1 || $showTextThis == 1}
	<div class="share-tools">
		<span class="share-tools-label hidden-inline-xs">SHARE</span>
		{if $showTextThis == 1}
			<a href="#" title="Text Title" onclick="return VuFind.GroupedWork.showSmsForm(this, '{$recordDriver->getPermanentId()|escape:"url"}')" title="Share via text message">
				<img src="{img filename='sms-icon.png'}" alt="Text This">
			</a>
		{/if}
		{if $showEmailThis == 1}
			<a href="#" onclick="return VuFind.GroupedWork.showEmailForm(this, '{$recordDriver->getPermanentId()|escape:"url"}')" title="Share via e-mail">
				<img src="{img filename='email-icon.png'}" alt="E-mail this">
			</a>
		{/if}
	</div>
	{/if}
{/strip}