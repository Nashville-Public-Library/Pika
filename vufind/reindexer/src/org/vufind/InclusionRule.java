package org.vufind;

import java.util.regex.Pattern;

/**
 * Information to determine if a particular record/item should be included within a given scope
 *
 * Pika
 * User: Mark Noble
 * Date: 7/10/2015
 * Time: 11:31 AM
 */
public class InclusionRule {
	private String recordType;
	private String locationCode;
	private Pattern locationCodePattern;
	private String subLocationCode;
	private Pattern subLocationCodePattern;
	private boolean includeHoldableOnly;
	private boolean includeItemsOnOrder;
	private boolean includeEContent;

	public InclusionRule(String recordType, String locationCode, String subLocationCode, boolean includeHoldableOnly, boolean includeItemsOnOrder, boolean includeEContent){
		this.recordType = recordType;
		this.locationCode = locationCode;
		this.subLocationCode = subLocationCode;
		this.includeHoldableOnly = includeHoldableOnly;
		this.includeItemsOnOrder = includeItemsOnOrder;
		this.includeEContent = includeEContent;

		if (locationCode.length() == 0){
			locationCode = ".*";
		}
		this.locationCodePattern = Pattern.compile(locationCode);
		if (subLocationCode.length() == 0){
			subLocationCode = ".*";
		}
		this.subLocationCodePattern = Pattern.compile(subLocationCode);
	}

	public boolean isItemIncluded(String recordType, String locationCode, String subLocationCode, boolean isHoldable, boolean isOnOrder, boolean isEContent){
		if (!this.recordType.equals(recordType)){
			return false;
		}
		if (locationCodePattern.matcher(locationCode).lookingAt() && subLocationCodePattern.matcher(subLocationCode).lookingAt()){
			//We got a match based on location
			if (includeHoldableOnly && !isHoldable){
				return false;
			}
			if (!includeItemsOnOrder && isOnOrder){
				return false;
			}
			if (!includeEContent && isEContent){
				return false;
			}
		}else{
			return false;
		}
		return true;
	}
}
