{* Test template *}
<form method="post" action="{$path}" name="popupForm" class="form-horizontal" id="shareURL">
	<div class="alert alert-info">
		<p>
		 Share this URL:
		</p>
	</div>
	<div class="form-group">
		<label for="related_record" class="col-sm-3">{translate text="Edition"}: </label>
		<div class="col-sm-9">
			<select name="related_record" id="related_record" class="form-control">
				<option selected="selected" value="">{translate text="Select an edition for more details"}</option>
				{foreach from=$relatedRecords key=val item=details}
					<option value="{$details.id}">{$details.format|escape}{if $details.edition} {$details.edition}{/if}{if $details.publisher} {$details.publisher}{/if}{if $details.publicationDate} {$details.publicationDate}{/if}</option>
				{/foreach}
			</select>
		</div>
	</div>
  </div>
</form>
