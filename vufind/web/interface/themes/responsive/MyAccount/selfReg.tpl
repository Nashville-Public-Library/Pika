{strip}
<h3>{translate text='Register for a Library Card'}</h3>
<div class="page">
		{if (isset($selfRegResult) && $selfRegResult.success)}
			<div id="selfRegSuccess" class="alert alert-success">
				{if $selfRegistrationSuccessMessage}
					{$selfRegistrationSuccessMessage}
				{else}
					Congratulations, you have successfully registered for a new library card.
					You will have limited privileges.<br>
					Please bring a valid ID to the library to receive a physical library card.
				{/if}
			</div>
			<div class="alert alert-info">
				Your library card number is <strong>{$selfRegResult.barcode}</strong>.
			</div>
			{if $pinSetSuccess}
				<div class="alert alert-info">
					{$pinSetSuccess}
				</div>
			{/if}
			{if $pinSetFail}
				<div class="alert alert-warning">
					{$pinSetFail}
				</div>
			{/if}
		{else}
			{img_assign filename='self_reg_banner.png' var=selfRegBanner}
			{if $selfRegBanner}
				<img src="{$selfRegBanner}" alt="Self Register for a new library card" class="img-responsive">
			{/if}

			<div id="selfRegDescription" class="alert alert-info">
				{if $selfRegistrationFormMessage}
					{$selfRegistrationFormMessage}
				{else}
					This page allows you to register as a patron of our library online. You will have limited privileges initially.
				{/if}
			</div>
			{if (isset($selfRegResult))}
				<div id="selfRegFail" class="alert alert-warning">
					Sorry, we were unable to create a library card for you.  You may already have an account or there may be an error with the information you entered.
					Please try again or visit the library in person (with a valid ID) so we can create a card for you.
				</div>
			{/if}
			{if $captchaMessage}
				<div id="selfRegFail" class="alert alert-warning">
				{$captchaMessage}
				</div>
			{/if}
			<div id="selfRegistrationFormContainer">
				{$selfRegForm}
			</div>
		{/if}
</div>
{/strip}
{if $promptForBirthDateInSelfReg}
<script type="text/javascript">
	{* #borrower_note is birthdate for anythink *}
	{* this is bootstrap datepicker, not jquery ui *}
	{literal}
	$(function(){
		$('input.datePika').datepicker({
			format: "mm-dd-yyyy"
			,endDate: "+0d"
			,startView: 2
		});
		{/literal}
{*  Guardian Name is required for users under 18 for Sacramento Public Library *}
		{literal}
		if ($('#guardianFirstName')){
			jQuery.validator.addMethod("california", function(value, element) {
				/*Must be state code for California*/
				return this.optional( element ) || /^CA|ca$/.test( value );
			}, 'Please enter CA. Only California Residents may register.');
			jQuery.validator.addMethod("californiaZIP", function(value, element) {
				 /*Must be zip code for California*/
				return this.optional( element ) || /^9/.test( value );
			}, 'Please enter zip code that starts with a 9. Only California Residents may register.');
			$('#zip').rules('add', {californiaZIP : true});
			$('#state').rules('add', {california : true});

			$('#birthDate').focusout(function(){
				var birthDate = $(this).datepicker('getDate');
				if (birthDate) {
					var today = new Date(),
							age = today.getFullYear() - birthDate.getFullYear();
					if (today.getMonth() < birthDate.getMonth() ||
							(today.getMonth() == birthDate.getMonth() && today.getDate() < birthDate.getDate())) {
						age--;
					}
					var isMinor = age < 18;
					/* Have to add/remove rule to each element separately, can't combine selector */
					$("#guardianFirstName").rules("add", {
						required:isMinor
					});
					$("#guardianLastName").rules("add", {
						required:isMinor
					});
					if (isMinor){
						if ( $('#propertyRowguardianFirstName label span.required-input').length == 0) {
							$('#propertyRowguardianFirstName label').append('<span class="required-input">*</span>');
						}
						$('#propertyRowguardianFirstName, #propertyRowguardianLastName').show();
						if ( $('#propertyRowguardianLastName label span.required-input').length == 0) {
							$('#propertyRowguardianLastName label').append('<span class="required-input">*</span>');
						}
					} else {
						$('#propertyRowguardianFirstName, #propertyRowguardianLastName').hide();
						$('#propertyRowguardianFirstName label, #propertyRowguardianLastName label').children('span.required-input').remove();
					}
				}
			});
		}
	});
	{/literal}
{/if}
		{* Pin Validation for CarlX, Sirsi, and Sacramento *}
		{literal}
	$(function(){
		$('#pin').rules('add', {minlength:{/literal}{if $pinMinimumLength}{$pinMinimumLength}{else}0{/if}{literal}});
		$('#pin1').rules('add', {equalTo: "#pin",minlength:{/literal}{if $pinMinimumLength}{$pinMinimumLength}{else}0{/if}{literal}});
	});
	{/literal}
</script>
