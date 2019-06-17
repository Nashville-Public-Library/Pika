{*{debug}*}
<!-- NB smarty tag strip will break the jquery below that allows user to navigate among selfreg screens. Don't use strip. -->
<h3>{translate text='Register for a Library Card'}</h3>
<div class="page">
		{if (isset($selfRegResult) && $selfRegResult.success)}
			<div id="selfRegSuccess" class="alert alert-success">
				{if $selfRegistrationSuccessMessage}
					{$selfRegistrationSuccessMessage}
				{else}
					<p>Congratulations, you have successfully registered for a new library card.</p>
					<p>Your library card number has been emailed to you.</p>
					<p>Please bring a valid ID to the library to receive a physical library card.</p>
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
					This page allows you to register as a patron of our library.
				{/if}
			</div>
			{if (isset($selfRegResult))}
				<div id="selfRegFail" class="alert alert-warning">
					Sorry, we were unable to create a library card for you.  You may already have an account, you may be too young to reigster online, or there may be an error with the information you entered. Please try again or visit the library in person (with a valid ID) so we can create a card for you.
				</div>
			{/if}
			{if $captchaMessage}
				<div id="selfRegFail" class="alert alert-warning">
				{$captchaMessage}
				</div>
			{/if}
			<div id="selfRegistrationFormContainer">
<script type="text/javascript">

{literal}

; NASHVILLESELFREG = {
	name: 'NASHVILLESELFREG',

// MOVE A CARD TO THE TOP
	top: function (cardValue) {
		$("*[id*=propertyRow").each(function() {
			$(this).hide();
		});
		$("#propertyRow" + cardValue).show();
		$("#propertyRow" + cardValue).find('div').each(function() {
			$(this).show();
		});
		$("#propertyRowNashvilleSelfRegNav").show();
	}		
}


	$( document ).ready(function() {
{/literal}
		const inLibrary		= "{$inLibrary}";
		const activeIp		= "{$activeIp}";
		const physicalLocation	= "{$physicalLocation}";
{literal}


// MOVE RECAPTCHA TO "CREATE ACCOUNT" CARD 8
		$("div.g-recaptcha").parent().css("background-color", "yellow").appendTo("#card_body_PaPeR_8>div.panel-body").children("div.g-recaptcha").unwrap();

// INITIAL FLOW: IN-LIBRARY OR ONLINE?
		if (inLibrary == "1") { 
			NASHVILLESELFREG.top("1A");
		} else {
			NASHVILLESELFREG.top("1B");
		}

// SET NAV: 1A
		$("#canShowIDSelect").on('change', function() {
			if( $("#canShowIDSelect").val() == 'Yes' && $("#authenticateAddressSelect").val() == 'Yes') {
				$("#continue").on('click', function() {
					NASHVILLESELFREG.top("3");
				});
			} else {
				$("#continue").on('click', function() {
					NASHVILLESELFREG.top("2A");
				});
			}
		});

		$("#authenticateAddressSelect").on('change', function() {
			if( $("#canShowIDSelect").val() == 'Yes' && $("#authenticateAddressSelect").val() == 'Yes') {
				$("#continue").on('click', function() {
					NASHVILLESELFREG.top("3");
				});
			} else {
				$("#continue").on('click', function() {
					NASHVILLESELFREG.top("2A");
				});
			}
		});

// SET NAV: 1B
		$("#eligibleOBRSelect").on('change', function() {
			if( $("#eligibleOBRSelect").val() == 'Yes') {
				$("#continue").on('click', function() {
					NASHVILLESELFREG.top("3");
				});
			} else {
				$("#continue").on('click', function() {
					NASHVILLESELFREG.top("2B");
				});
			}
		});

// SET NAV: 3
		$("#birthDate").on('change', function() {
			var today = new Date();
			var birthDate = new Date($("#birthDate").val());
			var age = today.getFullYear() - birthDate.getFullYear();
			var m = today.getMonth() - birthDate.getMonth();
			if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
				age--;
			}
			alert("age: " + age);
//			if ( inLibrary == 1 && $("#canShowIDSelect").val() == 'Yes' && $("#authenticateAddressSelect").val() == 'Yes') {
//				if ( age < 13 ) {
//					patronType = 2;
//				}

		});			


	});

{/literal}
</script>

				{$selfRegForm}

			</div>
		{/if}
</div>
