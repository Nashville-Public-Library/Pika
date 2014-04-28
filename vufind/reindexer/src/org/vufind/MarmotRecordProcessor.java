package org.vufind;

import org.apache.log4j.Logger;
import org.ini4j.Ini;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;

import java.sql.Connection;
import java.util.*;

/**
 * ILS Indexing with customizations specific to Marmot
 * VuFind-Plus
 * User: Mark Noble
 * Date: 2/21/14
 * Time: 3:00 PM
 */
public class MarmotRecordProcessor extends IlsRecordProcessor {
	public MarmotRecordProcessor(GroupedWorkIndexer indexer, Connection vufindConn, Ini configIni, Logger logger) {
		super(indexer, vufindConn, configIni, logger);
	}

	protected void loadAdditionalOwnershipInformation(GroupedWorkSolr groupedWork, String locationCode){
		groupedWork.addCollectionGroup(indexer.translateValue("collection_group", locationCode));
		//TODO: Make collections by library easier to define (in VuFind interface)
		if (additionalCollections != null){
			for (String additionalCollection : additionalCollections){
				groupedWork.addCollectionAdams(indexer.translateValue(additionalCollection, locationCode));
			}
		}
		ArrayList<String> subdomainsForLocation = getLibrarySubdomainsForLocationCode(locationCode);
		ArrayList<String> relatedLocationCodesForLocation = getRelatedLocationCodesForLocationCode(locationCode);
		groupedWork.addDetailedLocation(indexer.translateValue("detailed_location", locationCode), subdomainsForLocation, relatedLocationCodesForLocation);
	}

	protected void loadLocalCallNumbers(GroupedWorkSolr groupedWork, List<DataField> unsuppressedItems) {
		for (DataField curItem : unsuppressedItems){
			Subfield locationSubfield = curItem.getSubfield(locationSubfieldIndicator);
			if (locationSubfield != null){
				String locationCode = locationSubfield.getData();
				String callNumberPrestamp = "";
				if (callNumberPrestampSubfield != ' '){
					callNumberPrestamp = curItem.getSubfield(callNumberPrestampSubfield) == null ? "" : curItem.getSubfield(callNumberPrestampSubfield).getData();
				}
				String callNumber = "";
				if (callNumberSubfield != ' '){
					callNumber = curItem.getSubfield(callNumberSubfield) == null ? "" : curItem.getSubfield(callNumberSubfield).getData();
				}
				String callNumberCutter = "";
				if (callNumberCutterSubfield != ' '){
					callNumberCutter = curItem.getSubfield(callNumberCutterSubfield) == null ? "" : curItem.getSubfield(callNumberCutterSubfield).getData();
				}
				String fullCallNumber = callNumberPrestamp + callNumber + callNumberCutter;
				String sortableCallNumber = callNumber + callNumberCutter;
				if (fullCallNumber.length() > 0){
					ArrayList<String> subdomainsForLocation = getLibrarySubdomainsForLocationCode(locationCode);
					ArrayList<String> relatedLocationCodesForLocation = getRelatedLocationCodesForLocationCode(locationCode);
					groupedWork.addLocalCallNumber(fullCallNumber, subdomainsForLocation, relatedLocationCodesForLocation);
					groupedWork.addCallNumberSort(sortableCallNumber, subdomainsForLocation, relatedLocationCodesForLocation);
				}
			}
		}
	}

	/**
	 * Determine Record Format(s)
	 *
	 * @return Set format of record
	 */
	public Set<String> loadFormats(Record record, boolean returnFirst) {
		Set<String> result = new LinkedHashSet<String>();
		String leader = record.getLeader().toString();
		char leaderBit;
		ControlField fixedField = (ControlField) record.getVariableField("008");
		//DataField title = (DataField) record.getVariableField("245");
		char formatCode;

		// check for music recordings quickly so we can figure out if it is music
		// for category (needto do here since checking what is on the Compact
		// Disc/Phonograph, etc is difficult).
		if (leader.length() >= 6) {
			leaderBit = leader.charAt(6);
			switch (Character.toUpperCase(leaderBit)) {
				case 'J':
					result.add("MusicRecording");
					break;
			}
		}
		if (result.size() >= 1 && returnFirst)
			return result;

		// check for playaway in 260|b
		DataField sysDetailsNote = (DataField) record.getVariableField("260");
		if (sysDetailsNote != null) {
			if (sysDetailsNote.getSubfield('b') != null) {
				String sysDetailsValue = sysDetailsNote.getSubfield('b').getData()
						.toLowerCase();
				if (sysDetailsValue.contains("playaway")) {
					result.add("Playaway");
					if (returnFirst)
						return result;
				}
			}
		}

		// Check for formats in the 538 field
		DataField sysDetailsNote2 = (DataField) record.getVariableField("538");
		if (sysDetailsNote2 != null) {
			if (sysDetailsNote2.getSubfield('a') != null) {
				String sysDetailsValue = sysDetailsNote2.getSubfield('a').getData()
						.toLowerCase();
				if (sysDetailsValue.contains("playaway")) {
					result.add("Playaway");
					if (returnFirst)
						return result;
				} else if (sysDetailsValue.contains("bluray")
						|| sysDetailsValue.contains("blu-ray")) {
					result.add("Blu-ray");
					if (returnFirst)
						return result;
				} else if (sysDetailsValue.contains("dvd")) {
					result.add("DVD");
					if (returnFirst)
						return result;
				} else if (sysDetailsValue.contains("vertical file")) {
					result.add("VerticalFile");
					if (returnFirst)
						return result;
				}
			}
		}

		// Check for formats in the 500 tag
		DataField noteField = (DataField) record.getVariableField("500");
		if (noteField != null) {
			if (noteField.getSubfield('a') != null) {
				String noteValue = noteField.getSubfield('a').getData().toLowerCase();
				if (noteValue.contains("vertical file")) {
					result.add("VerticalFile");
					if (returnFirst)
						return result;
				}
			}
		}

		// check if there's an h in the 245
		/*if (title != null) {
			if (title.getSubfield('h') != null) {
				if (title.getSubfield('h').getData().toLowerCase()
						.contains("[electronic resource]")) {
					result.add("Electronic");
					if (returnFirstValue)
						return result;
				}
			}
		}*/

		// Check for large print book (large format in 650, 300, or 250 fields)
		// Check for blu-ray in 300 fields
		DataField edition = (DataField) record.getVariableField("250");
		if (edition != null) {
			if (edition.getSubfield('a') != null) {
				if (edition.getSubfield('a').getData().toLowerCase()
						.contains("large type")) {
					result.add("LargePrint");
					if (returnFirst)
						return result;
				}
			}
		}

		@SuppressWarnings("unchecked")
		List<DataField> physicalDescription = getDataFields(record, "300");
		if (physicalDescription != null) {
			Iterator<DataField> fieldsIter = physicalDescription.iterator();
			DataField field;
			while (fieldsIter.hasNext()) {
				field = fieldsIter.next();
				@SuppressWarnings("unchecked")
				List<Subfield> subFields = field.getSubfields();
				for (Subfield subfield : subFields) {
					if (subfield.getData().toLowerCase().contains("large type")) {
						result.add("LargePrint");
						if (returnFirst)
							return result;
					} else if (subfield.getData().toLowerCase().contains("bluray")
							|| subfield.getData().toLowerCase().contains("blu-ray")) {
						result.add("Blu-ray");
						if (returnFirst)
							return result;
					}
				}
			}
		}
		@SuppressWarnings("unchecked")
		List<DataField> topicalTerm = getDataFields(record, "650");
		if (topicalTerm != null) {
			Iterator<DataField> fieldsIter = topicalTerm.iterator();
			DataField field;
			while (fieldsIter.hasNext()) {
				field = fieldsIter.next();
				@SuppressWarnings("unchecked")
				List<Subfield> subfields = field.getSubfields();
				for (Subfield subfield : subfields) {
					if (subfield.getData().toLowerCase().contains("large type")) {
						result.add("LargePrint");
						if (returnFirst)
							return result;
					}
				}
			}
		}

		@SuppressWarnings("unchecked")
		List<DataField> localTopicalTerm = getDataFields(record, "690");
		if (localTopicalTerm != null) {
			Iterator<DataField> fieldsIterator = localTopicalTerm.iterator();
			DataField field;
			while (fieldsIterator.hasNext()) {
				field = fieldsIterator.next();
				Subfield subfieldA = field.getSubfield('a');
				if (subfieldA != null) {
					if (subfieldA.getData().toLowerCase().contains("seed library")) {
						result.add("SeedPacket");
						if (returnFirst)
							return result;
					}
				}
			}
		}

		// check the 007 - this is a repeating field
		@SuppressWarnings("unchecked")
		List<DataField> fields = getDataFields(record, "007");
		if (fields != null) {
			Iterator<DataField> fieldsIter = fields.iterator();
			ControlField formatField;
			while (fieldsIter.hasNext()) {
				formatField = (ControlField) fieldsIter.next();
				if (formatField.getData() == null || formatField.getData().length() < 2) {
					continue;
				}
				// Check for blu-ray (s in position 4)
				// This logic does not appear correct.
				/*
				 * if (formatField.getData() != null && formatField.getData().length()
				 * >= 4){ if (formatField.getData().toUpperCase().charAt(4) == 'S'){
				 * result.add("Blu-ray"); break; } }
				 */
				formatCode = formatField.getData().toUpperCase().charAt(0);
				switch (formatCode) {
					case 'A':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'D':
								result.add("Atlas");
								break;
							default:
								result.add("Map");
								break;
						}
						break;
					case 'C':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'A':
								result.add("TapeCartridge");
								break;
							case 'B':
								result.add("ChipCartridge");
								break;
							case 'C':
								result.add("DiscCartridge");
								break;
							case 'F':
								result.add("TapeCassette");
								break;
							case 'H':
								result.add("TapeReel");
								break;
							case 'J':
								result.add("FloppyDisk");
								break;
							case 'M':
							case 'O':
								result.add("CDROM");
								break;
							case 'R':
								// Do not return - this will cause anything with an
								// 856 field to be labeled as "Electronic"
								break;
							default:
								result.add("Software");
								break;
						}
						break;
					case 'D':
						result.add("Globe");
						break;
					case 'F':
						result.add("Braille");
						break;
					case 'G':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'C':
							case 'D':
								result.add("Filmstrip");
								break;
							case 'T':
								result.add("Transparency");
								break;
							default:
								result.add("Slide");
								break;
						}
						break;
					case 'H':
						result.add("Microfilm");
						break;
					case 'K':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'C':
								result.add("Collage");
								break;
							case 'D':
								result.add("Drawing");
								break;
							case 'E':
								result.add("Painting");
								break;
							case 'F':
								result.add("Print");
								break;
							case 'G':
								result.add("Photonegative");
								break;
							case 'J':
								result.add("Print");
								break;
							case 'L':
								result.add("Drawing");
								break;
							case 'O':
								result.add("FlashCard");
								break;
							case 'N':
								result.add("Chart");
								break;
							default:
								result.add("Photo");
								break;
						}
						break;
					case 'M':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'F':
								result.add("VideoCassette");
								break;
							case 'R':
								result.add("Filmstrip");
								break;
							default:
								result.add("MotionPicture");
								break;
						}
						break;
					case 'O':
						result.add("Kit");
						break;
					case 'Q':
						result.add("MusicalScore");
						break;
					case 'R':
						result.add("SensorImage");
						break;
					case 'S':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'D':
								if (formatField.getData().length() >= 4) {
									char speed = formatField.getData().toUpperCase().charAt(3);
									if (speed >= 'A' && speed <= 'E') {
										result.add("Phonograph");
									} else if (speed == 'F') {
										result.add("CompactDisc");
									} else if (speed >= 'K' && speed <= 'R') {
										result.add("TapeRecording");
									} else {
										result.add("SoundDisc");
									}
								} else {
									result.add("SoundDisc");
								}
								break;
							case 'S':
								result.add("SoundCassette");
								break;
							default:
								result.add("SoundRecording");
								break;
						}
						break;
					case 'T':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'A':
								result.add("Book");
								break;
							case 'B':
								result.add("LargePrint");
								break;
						}
						break;
					case 'V':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'C':
								result.add("VideoCartridge");
								break;
							case 'D':
								result.add("VideoDisc");
								break;
							case 'F':
								result.add("VideoCassette");
								break;
							case 'R':
								result.add("VideoReel");
								break;
							default:
								result.add("Video");
								break;
						}
						break;
				}
				if (returnFirst && !result.isEmpty()) {
					return result;
				}
			}
			if (!result.isEmpty() && returnFirst) {
				return result;
			}
		}

		// check the Leader at position 6
		if (leader.length() >= 6) {
			leaderBit = leader.charAt(6);
			switch (Character.toUpperCase(leaderBit)) {
				case 'C':
				case 'D':
					result.add("MusicalScore");
					break;
				case 'E':
				case 'F':
					result.add("Map");
					break;
				case 'G':
					// We appear to have a number of items without 007 tags marked as G's.
					// These seem to be Videos rather than Slides.
					// result.add("Slide");
					result.add("Video");
					break;
				case 'I':
					result.add("SoundRecording");
					break;
				case 'J':
					result.add("MusicRecording");
					break;
				case 'K':
					result.add("Photo");
					break;
				case 'M':
					result.add("Electronic");
					break;
				case 'O':
				case 'P':
					result.add("Kit");
					break;
				case 'R':
					result.add("PhysicalObject");
					break;
				case 'T':
					result.add("Manuscript");
					break;
			}
		}
		if (!result.isEmpty() && returnFirst) {
			return result;
		}

		if (leader.length() >= 7) {
			// check the Leader at position 7
			leaderBit = leader.charAt(7);
			switch (Character.toUpperCase(leaderBit)) {
				// Monograph
				case 'M':
					if (result.isEmpty()) {
						result.add("Book");
					}
					break;
				// Serial
				case 'S':
					// Look in 008 to determine what type of Continuing Resource
					formatCode = fixedField.getData().toUpperCase().charAt(21);
					switch (formatCode) {
						case 'N':
							result.add("Newspaper");
							break;
						case 'P':
							result.add("Journal");
							break;
						default:
							result.add("Serial");
							break;
					}
			}
		}

		// Nothing worked!
		if (result.isEmpty()) {
			result.add("Unknown");
		}

		return result;
	}
}
