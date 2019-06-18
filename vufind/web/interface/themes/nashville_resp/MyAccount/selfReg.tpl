<link rel="stylesheet" type="text/css" href="/jquery.steps.css" />
<link rel="stylesheet" type="text/css" href="/jquery.steps.examples.css" />
<link rel="stylesheet" type="text/css" href="/main.css" />
<script src="/jquery.steps.min.js"></script>

<form id="NashvilleSelfReg" action="#">
	<h1>Get a Library Card</h1>
	<fieldset>
		<legend>Let's get started!</legend>
		<label for="birthDate">Date of Birth</label>
		<input id="birthDate" name="birthDate" type="date">
		<label for="resident">Do you reside in Davidson County?</label>
		<select id="resident" name="resident">
			<option></option>
			<option value="N">No</option>
			<option value="Y">Yes</option>
		</select>
		<label for="zip">Home ZIP code</label>
		<input id="zip" name="zip" type="text" maxLength="5" pattern="[0-9]{5}">
		<label for="canShowID">Can you show an approved form of ID at the desk?
			<a href="/Help/Home?topic=papr" class="modalDialogTrigger" data-title="Help with PAPR">help</a>
		</label>
		<select id="canShowID" name="canShowID">
			<option></option>
			<option value="N">No</option>
			<option value="Y">Yes</option>
		</select>
		<label for="canAuthenticateAddress">Does this ID display your current address or do you have proof of your current address to show at the desk (recent postmarked mail, etc.)?
			<a href="/Help/Home?topic=papr" class="modalDialogTrigger" data-title="Help with PAPR">help</a>
		</label>
		<select id="canAuthenticateAddress" name="canAuthenticateAddress">
			<option></option>
			<option value="N">No</option>
			<option value="Y">Yes</option>
		</select>
		<label for="ageGroup">Age Group</label>
		<select id="ageGroup" name="ageGroup" class="uneditable-input">
			<option selected></option>
			<option disabled>A</option>
			<option disabled>C</option>
			<option disabled>T</option>
		</select>	
		<label for="patronType">Patron Type</label>
		<select id="patronType" name="patronType" class="uneditable-input">
			<option selected></option>
			<option disabled>1</option>
			<option disabled>2</option>
			<option disabled>3</option>
			<option disabled>6</option>
			<option disabled>10</option>
			<option disabled>11</option>
			<option disabled>14</option>
			<option disabled>15</option>
			<option disabled>20</option>
			<option disabled>43</option>
			<option disabled>44</option>
			<option disabled>45</option>
		</select>
	</fieldset>
	<h1>Patron contact information</h1>
	<fieldset>
		<label for="firstName" title="Your first name">First Name</label>
		<input type="text" name="firstName" id="firstName" value="" >
		<label for="middleName" title="Your middle name">Middle Name</label>
		<input type="text" name="middleName" id="middleName" value="" >
		<label for="lastName" title="Your last name">Last Name</label>
		<input type="text" name="lastName" id="lastName" value="" >
		<label for="birthDate_2" title="Your date of birth">Date of Birth (MM/DD/YYYY)</label>
		<input type="date" name="birthDate_2" id="birthDate_2" value="" >
		<label for="address" title="Mailing Address">Mailing Address</label>
		<input type="text" name="address" id="address" value="" >
		<label for="city" title="City">City</label>
		<input type="text" name="city" id="city" value="" >
		<label for="state" title="State">State</label>
		<input type="text" name="state" id="state" value="TN" >
		<label for="zip_2" title="Zip Code">Zip Code</label>
		<input type="text" name="zip_2" id="zip_2" value="" >
		<label for="phone" title="Primary Phone">Primary Phone</label>
		<input type="text" name="phone" id="phone" value="" >
		<label for="email" title="E-Mail">E-Mail</label>
		<input type="text" name="email" id="email" value="" >
	</fieldset>
	<h1>Guarantor</h1>
	<fieldset>
		<label for="guarantorFirstName" title="Guarantor first name">Guarantor First Name*</label>
		<input type="text" name="guarantorFirstName" id="guarantorFirstName" value="">
		<label for="guarantorMiddleName" title="Guarantor middle name">Guarantor Middle Name</label>
		<input type="text" name="guarantorMiddleName" id="guarantorMiddleName" value="">
		<label for="guarantorLastName" title="Guarantor last name">Guarantor Last Name*</label>
		<input type="text" name="guarantorLastName" id="guarantorLastName" value="">
		<label for="guarantorBirthDate" title="Guarantor Date of birth">Guarantor Date of Birth (MM/DD/YYYY)*</label>
		<input type="text" name="guarantorBirthDate" id="guarantorBirthDate" value="">
		<label for="guarantorRelationshipSelect" title="Guarantor Relationship">Guarantor Relationship*</label>
		<select name="guarantorRelationship" id="guarantorRelationshipSelect">
			<option value="Parent">Parent</option>
			<option value="Grandparent">Grandparent</option>
			<option value="Sibling">Sibling</option>
			<option value="Legal guardian">Legal guardian</option>
		</select>
	</fieldset>
	<h1>Approved User</h1>
	<fieldset>
		<label for="approvedUserFirstName" title="Approved User first name">Approved User First Name*</label>
		<input type="text" name="approvedUserFirstName" id="approvedUserFirstName" value="">
		<label for="approvedUserMiddleName" title="Approved User middle name">Approved User Middle Name</label>
		<input type="text" name="approvedUserMiddleName" id="approvedUserMiddleName" value="">
		<label for="approvedUserLastName" title="Approved User last name">Approved User Last Name*</label>
		<input type="text" name="approvedUserLastName" id="approvedUserLastName" value="">
		<label for="approvedUserBirthDate" title="Approved User Date of birth">Approved User Date of Birth (MM/DD/YYYY)*</label>
		<input type="text" name="approvedUserBirthDate" id="approvedUserBirthDate" value="">
	</fieldset>
</form>

{literal}
<script type="text/javascript">

var form = $("#NashvilleSelfReg").show();

$.validator.addMethod(
	"isChild",
	function(value, element) {
		return this.optional(element) || $("#ageGroup").val() == "C";
	}
);

$.validator.addMethod(
	"isOBR",
	function(value, element) {
		return this.optional(element) || $("#patronType").val() == "10";
	}
);

$.validator.addMethod(
	"validNashvilleZIP", 
	function(value, element) {
		var validZips = {
			"37013": "ANTIOCH",
			"37015": "ASHLAND CITY",
			"37027": "BRENTWOOD",
			"37064": "FRANKLIN",
			"37072": "GOODLETTSVILLE",
			"37076": "HERMITAGE",
			"37080": "JOELTON",
			"37086": "LA VERGNE",
			"37115": "MADISON",
			"37122": "MOUNT JULIET",
			"37135": "NOLENSVILLE",
			"37138": "OLD HICKORY",
			"37143": "PEGRAM",
			"37152": "RIDGETOP",
			"37189": "WHITES CREEK",
			"37201": "NASHVILLE",
			"37203": "NASHVILLE",
			"37204": "NASHVILLE",
			"37205": "NASHVILLE",
			"37206": "NASHVILLE",
			"37207": "NASHVILLE",
			"37208": "NASHVILLE",
			"37209": "NASHVILLE",
			"37210": "NASHVILLE",
			"37211": "NASHVILLE",
			"37212": "NASHVILLE",
			"37213": "NASHVILLE",
			"37214": "NASHVILLE",
			"37215": "NASHVILLE",
			"37216": "NASHVILLE",
			"37217": "NASHVILLE",
			"37218": "NASHVILLE",
			"37219": "NASHVILLE",
			"37220": "NASHVILLE",
			"37221": "NASHVILLE",
			"37228": "NASHVILLE",
			"37232": "NASHVILLE",
			"37240": "NASHVILLE"};
		return this.optional(element) || (value in validZips);
	}, 
	"* ZIP must be in Nashville/Davidson County"
);

form.steps({

	headerTag: "h1",
	bodyTag: "fieldset",

	onStepChanging: function (event, currentIndex, newIndex) {
		// Always allow previous action even if the current form is not valid!
		if (currentIndex > newIndex) {
			return true;
		}
		// Needed in some cases if the user went back (clean up)
		if (currentIndex < newIndex) {
		// To remove error styles
			form.find(".body:eq(" + newIndex + ") label.error").remove();
			form.find(".body:eq(" + newIndex + ") .error").removeClass("error");
		}
		if (currentIndex === 0) {

// SET PATRON TYPE FROM BIRTHDATE, RESIDENT, INLIBRARY, AUTH
			var today = new Date();
			var birthDate = new Date($("#birthDate").val());
			var resident = $("#resident").val();
			//var inLibrary = $inLibrary;
			var inLibrary = "N";
			var canShowID = $("#canShowID").val();
			var canAuthenticateAddress = $("#canAuthenticateAddress").val();
			var age = today.getFullYear() - birthDate.getFullYear();
			var ageGroup = "";
			var m = today.getMonth() - birthDate.getMonth();
			if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
				age--;
			}
			if (age >= 0 && age <= 12) { 
				ageGroup = "C"; 
			} else if (age >= 13 && age <= 17) { 
				ageGroup = "T";
			} else if (age >= 18 && age <= 113) { 
				ageGroup = "A"; 
			}
			$("#ageGroup")
				.empty()
				.append('<option selected="selected" value="'+ageGroup+'">'+ageGroup+'</option>')
			;
			var auth;
			if (canShowID == "Y" && canAuthenticateAddress == "Y") {
				auth = "Y";
			} else {
				auth = "N";
			}
			var patronString = ageGroup + resident + inLibrary + auth;
			var patronStringPatterns = {
				"[AT]YN.":"10",
				"[CT].YN":"20",
				"A.YN":"6",
				"ANN.":"3",
				"ANYY":"45",
				"AYYY":"1",
				"CNN.":"15",
				"CNYY":"43",
				"CYN.":"2",
				"CYYY":"2",
				"TNN.":"14",
				"TNYY":"44",
				"TYYY":"11"};
			for (let [pattern, bty] of Object.entries(patronStringPatterns)) {
				var found = patronString.match(pattern);
				if (found !== null) {
					$("#patronType")
						.empty()
						.append('<option selected="selected" value="'+bty+'">'+bty+'</option>')
					;
				}
			};
// END SET PATRON TYPE
// UPDATE RELATED FIELDS
			$("#birthDate_2").val($("#birthDate").val());
			$("#zip_2").val($("#zip").val());
		}
		if (currentIndex === 1) {
// SKIP GUARDIAN IF NOT A CHILD
			if ($("#ageGroup").val() !== "C") {
//				form.steps("next");
			}
			if (priorIndex === 2) {
//				form.steps("previous");
			}
		}
		form.validate().settings.ignore = ":disabled,:hidden";
		return form.valid();
	},

	onStepChanged: function (event, currentIndex, priorIndex) {

	},

	onFinishing: function (event, currentIndex) {
		form.validate().settings.ignore = ":disabled";
		return form.valid();
	},

	onFinished: function (event, currentIndex) {
		alert("Submitted!");
	}

});

$("#NashvilleSelfReg").validate({

	debug: true,

	errorPlacement: function errorPlacement(error, element) { element.before(error); },

	rules: {
		birthDate: {
			required: true
		},
		canAuthenticateAddress: {
			required: true
		},
		canShowID: {
			required: true
		},
		resident: {
			required: true
		},
		zip: {
			required: true,
			validNashvilleZIP: {
				depends: function(element) {
					return $("#resident").val() == "Y";
				}
			}
		},
		firstName: {
			required: true
		},
		lastName: {
			required: true
		},
		birthDate_2: {
			required: true
		},
		address: {
		},
		city: {
		},
		state: {
		},
		zip_2: {
		},
		phone: {
			required: {
				depends: function(element) {
					if ($("#email").val() == "" && $("#patronType").val() !== "10") {
						return true;
					}
				}
			},
			phoneUS: true
		},
		email: {
			required: {
				depends: function(element) {
					if ($("#phone").val() == "" || $("#patronType").val() == "10") {
						return true;
					}
				}
			},
			email: true
		}
	},
	messages: {
		zip: {
			required: "We must have your residential ZIP code.",
			validNashvilleZIP: "You claimed you live in Nashville. Prove it with a valid ZIP code."
		},
		phone: {
			required: "We must have either your phone number or your email address."
		},
		email: {
			isOBR: "We must have your email address.",
			required: "We must have either your phone number or your email address."
		}
	}
}); 

</script>
{/literal}


