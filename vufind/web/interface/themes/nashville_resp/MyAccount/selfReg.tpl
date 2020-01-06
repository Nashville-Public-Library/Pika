{strip}
<h3>{translate text='Register for a Library Card'}</h3>
<div class="page">
		{if (isset($selfRegResult) && $selfRegResult.success)}
			<div id="selfRegSuccess" class="alert alert-success">
				{if $selfRegistrationSuccessMessage}
					{$selfRegistrationSuccessMessage}
				{else}
					<p>Congratulations, you have successfully registered for a new library card.</p>
					<p>Your library card number has been emailed to you. This gives you immediate access to our online streaming, download, and database content for 45 days.</p>
					<p>To maintain access indefinitely, visit any <a href="https://library.nashville.org/locations">NPL branch</a> with photo ID and <a href="https://library.nashville.org/get-card#getting-a-card">proof of Davidson County residency</a>.</p>
				{/if}
			</div>
		{else}
			{img_assign filename='self_reg_banner.png' var=selfRegBanner}
			{if $selfRegBanner}
				<img src="{$selfRegBanner}" alt="Self Register for a new library card" class="img-responsive">
			{/if}

			<div id="selfRegDescription" class="alert alert-info">
				{if $selfRegistrationFormMessage}
					{$selfRegistrationFormMessage}
				{else}
					<p>Residents of Davidson County or the City of Goodlettsville may register for a digital access card. We will email you a card number that gives you immediate access to our online streaming, download, and database content for 45 days. To maintain access indefinitely, visit any <a href="https://library.nashville.org/locations">NPL branch</a> with photo ID and <a href="https://library.nashville.org/get-card#getting-a-card">proof of Davidson County residency</a>.</p>

<p>By completing this form, you are agreeing to receive news and updates from Nashville Public Library and <a href="https://nplf.org">Nashville Public Library Foundation</a>.</p>

<p>Requirements:</p>
<ul>
    <li>You must be age 13 or older</li>
    <li>You must live in Davidson County or Goodlettsville</li>
    <li>You must provide your email address</li>

				{/if}
			</div>
			{if (isset($selfRegResult))}
				<div id="selfRegFail" class="alert alert-warning">
					Sorry, we were unable to create a library card for you.  You may already have an account, you may be too young to register online, or there may be an error with the information you entered. Please try again or visit the library in person (with a valid ID) so we can create a card for you.
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
	$(document).ready(function(){
		$('input.datePika').datepicker({
			format: "mm-dd-yyyy"
			,endDate: "+0d"
			,startView: 2
		});
	});
	{/literal}
	{* Pin Validation for CarlX, Sirsi *}
	{literal}
	if ($('#pin').length > 0 && $('#pin1').length > 0) {
		$("#objectEditor").validate({
			rules: {
				pin: {
					minlength: 4
				},
				pin1: {
					minlength: 4,
					equalTo: "#pin"
				}
			}
		});
	}
	{/literal}

</script>
{/if}
