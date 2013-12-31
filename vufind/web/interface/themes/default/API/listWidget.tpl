<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html lang="{$userLang}" class="embeddedListWidget">
{strip}
<head>
	<title>{$widget->name}</title>
  <meta http-equiv="Content-Type" content="text/html;charset=utf-8" />
  {css filename="consolidated.min.css"}
  
  <script type="text/javascript">
    path = '{$path}';
  </script>
    
  {js filename="consolidated.min.js"}
  {if $widget->customCss}
  	<link rel="stylesheet" type="text/css" href="{$widget->customCss}" />
  {/if}
	{if $additionalCss}
		<style type="text/css">
			{$additionalCss}
		</style>
	{/if}
  <base href="{$path}" target="_parent" />
</head>

<body class="embeddedListWidgetBody">
	{include file='API/listWidgetTabs.tpl'}
  
</body>
</html>
{/strip}