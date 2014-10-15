<form action="#" method="post" class="form form-horizontal" id="emailSearchForm">
	<div class="form-group">
		<label for="to" class="col-sm-3">{translate text='To'}:</label>
		<div class="col-sm-9">
			<input type="email" name="to" id="to" size="40" class="input-xxlarge required email form-control"><br />
		</div>
	</div>
	<div class="form-group">
		<label for="from" class="col-sm-3">{translate text='From'}:</label>
		<div class="col-sm-9">
			<input type="email" name="from" id="from" size="40" class="input-xxlarge required email form-control"><br />
		</div>
	</div>
	<div class="form-group">
		<label for="message" class="col-sm-3">{translate text='Message'}:</label>
		<div class="col-sm-9">
			<textarea name="message" id="message" rows="3" cols="40" class="input-xxlarge form-control"></textarea><br />
		</div>
	</div>
</form>

<script type="text/javascript">
	{literal}
	$("#emailSearchForm").validate({
		submitHandler: function(){
			VuFind.Searches.sendEmail();
		}
	});
	{/literal}
</script>