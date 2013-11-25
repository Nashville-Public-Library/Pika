package org.vufind;

import org.marc4j.marc.*;

import java.sql.*;
import java.util.*;
//import java.util.regex.Pattern;

/**
 * Description goes here
 * Rampart Marc Conversion
 * User: Mark Noble
 * Date: 10/17/13
 * Time: 9:26 AM
 */
public class RecordGroupingProcessor {
	private PreparedStatement getGroupedWorkStmt;
	private PreparedStatement insertGroupedWorkStmt;
	private PreparedStatement addIdentifierToGroupedWorkStmt;
	private PreparedStatement getGroupedWorkIdentifiersStmt;
	private PreparedStatement getGroupedWorksForIdentifierStmt;
	private PreparedStatement updateLinksToDifferentTitlesForIdentifier;
	private PreparedStatement getRecordWithoutSubfieldStmt;
	private PreparedStatement getRecordIgnoringSubfieldStmt;
	private PreparedStatement updateRecordSubtitleStmt;

	public RecordGroupingProcessor(Connection dbConnection) {
		try{
			getGroupedWorkStmt = dbConnection.prepareStatement("SELECT id FROM grouped_work where permanent_id = ?",  ResultSet.TYPE_FORWARD_ONLY,  ResultSet.CONCUR_READ_ONLY);
			insertGroupedWorkStmt = dbConnection.prepareStatement("INSERT INTO grouped_work (title, subtitle, author, grouping_category, permanent_id) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS) ;
			addIdentifierToGroupedWorkStmt = dbConnection.prepareStatement("INSERT INTO grouped_work_identifiers (grouped_work_id, type, identifier) VALUES (?, ?, ?)");
			getGroupedWorkIdentifiersStmt = dbConnection.prepareStatement("SELECT * FROM grouped_work_identifiers WHERE grouped_work_id=?",  ResultSet.TYPE_FORWARD_ONLY,  ResultSet.CONCUR_READ_ONLY);
			getGroupedWorksForIdentifierStmt = dbConnection.prepareStatement("SELECT * FROM grouped_work INNER JOIN grouped_work_identifiers ON grouped_work.id = grouped_work_id WHERE type = ? and identifier = ? and grouping_category = ? and linksToDifferentTitles = 0",  ResultSet.TYPE_FORWARD_ONLY,  ResultSet.CONCUR_READ_ONLY);
			updateLinksToDifferentTitlesForIdentifier = dbConnection.prepareStatement("UPDATE grouped_work_identifiers SET linksToDifferentTitles = 1 where type=? and identifier = ?");
			getRecordWithoutSubfieldStmt = dbConnection.prepareStatement("SELECT * FROM grouped_work where title = ? AND author = ? AND grouping_category=? AND subtitle = ''");
			getRecordIgnoringSubfieldStmt = dbConnection.prepareStatement("SELECT * FROM grouped_work where title = ? AND author = ? AND grouping_category=?");
			updateRecordSubtitleStmt = dbConnection.prepareStatement("UPDATE grouped_work set permanent_id = ?, subtitle = ? where id = ?");
		}catch (Exception e){
			System.out.println("Error setting up prepared statements");
		}
	}

	public GroupedWork getGroupedWorkForMarc(Record marcRecord){
		//Get data for the grouped record

		//Title
		DataField field245 = (DataField)marcRecord.getVariableField("245");
		String groupingTitle = null;
		String groupingSubtitle = null;
		if (field245 != null && field245.getSubfield('a') != null){
			String fullTitle = field245.getSubfield('a').getData();

			char nonFilingCharacters = field245.getIndicator2();
			if (nonFilingCharacters == ' ') nonFilingCharacters = '0';
			int numNonFilingCharacters = Integer.parseInt(Character.toString(nonFilingCharacters));

			groupingTitle = normalizeTitle(fullTitle, numNonFilingCharacters);

			//Add in subtitle (subfield b as well to avoid problems with gov docs, etc)
			if (field245.getSubfield('b') != null){
				groupingSubtitle = field245.getSubfield('b').getData();
				groupingSubtitle = normalizeSubtitle(groupingSubtitle);
			}
		}

		//Author
		String author = null;
		DataField field100 = (DataField)marcRecord.getVariableField("100");
		if (field100 != null && field100.getSubfield('a') != null){
			author = field100.getSubfield('a').getData();
		}else{
			DataField field110 = (DataField)marcRecord.getVariableField("110");
			if (field110 != null && field110.getSubfield('a') != null){
				author = field110.getSubfield('a').getData();
			}
		}
		String groupingAuthor = null;
		if (author != null){
			groupingAuthor = normalizeAuthor(author);
		}

		//Format
		String format = getFormat(marcRecord);
		String groupingFormat = categoryMap.get(formatsToGroupingCategory.get(format));

		//Identifiers
		HashSet<RecordIdentifier> identifiers = getIdentifiersFromMarcRecord(marcRecord);

		GroupedWork workForTitle = new GroupedWork();
		workForTitle.title = (groupingTitle == null ? "" : groupingTitle);
		workForTitle.subtitle = (groupingSubtitle == null ? "" : groupingSubtitle);
		workForTitle.author = (groupingAuthor == null ? "" : groupingAuthor);
		workForTitle.groupingCategory = groupingFormat;
		workForTitle.identifiers = identifiers;

		return workForTitle;
	}

	private HashSet<RecordIdentifier> getIdentifiersFromMarcRecord(Record marcRecord) {
		HashSet<RecordIdentifier> identifiers = new HashSet<RecordIdentifier>();
		List<DataField> field907 = getDataFields(marcRecord, "907");
		for (DataField cur907 : field907){
			Subfield subfieldA = cur907.getSubfield('a');
			if (subfieldA != null && subfieldA.getData().length() > 2){
				if (cur907.getSubfield('a').getData().substring(0,2).equals(".b") || cur907.getSubfield('a').getData().substring(0,2).equals(".o")){
					String recordNumber = cur907.getSubfield('a').getData();
					RecordIdentifier identifier = new RecordIdentifier();
					identifier.type = "ils";
					identifier.identifier = recordNumber;
					identifiers.add(identifier);
				}
			}
		}

		//Load identifiers
		List<DataField> identifierFields = getDataFields(marcRecord, new String[]{"020", "024", "022", "035"});
		for (DataField identifierField : identifierFields){
			if (identifierField.getSubfield('a') != null){
				String identifierValue = identifierField.getSubfield('a').getData().trim();
				//Get rid of any extra data at the end of the identifier
				if (identifierValue.indexOf(' ') > 0){
					identifierValue = identifierValue.substring(0, identifierValue.indexOf(' '));
				}
				String identifierType;
				if (identifierField.getTag().equals("020")){
					identifierType = "isbn";
					identifierValue = identifierValue.replaceAll("\\D", "");
					if (identifierValue.length() == 10){
						identifierValue = convertISBN10to13(identifierValue);
					}
				}else if (identifierField.getTag().equals("024")){
					identifierType = "upc";
				}else if (identifierField.getTag().equals("022")){
					identifierType = "issn";
				}else {
					identifierType = "oclc";
					identifierValue = identifierValue.replaceAll("\\(.*\\)", "");
				}
				RecordIdentifier identifier = new RecordIdentifier();
				if (identifierValue.length() > 20){
					continue;
				}else if (identifierValue.length() == 0){
					continue;
				}
				identifier.identifier = identifierValue;
				identifier.type = identifierType;
				identifiers.add(identifier);
			}
		}
		return identifiers;
	}

	private List<DataField> getDataFields(Record marcRecord, String tag) {
		List variableFields = marcRecord.getVariableFields(tag);
		List<DataField> variableFieldsReturn = new ArrayList<DataField>();
		for (Object variableField : variableFields){
			if (variableField instanceof DataField){
				variableFieldsReturn.add((DataField)variableField);
			}
		}
		return variableFieldsReturn;
	}

	private List<DataField> getDataFields(Record marcRecord, String[] tags) {
		List variableFields = marcRecord.getVariableFields(tags);
		List<DataField> variableFieldsReturn = new ArrayList<DataField>();
		for (Object variableField : variableFields){
			if (variableField instanceof DataField){
				variableFieldsReturn.add((DataField)variableField);
			}
		}
		return variableFieldsReturn;
	}

	private String normalizeAuthor(String author) {
		String groupingAuthor = author.replaceAll("[^\\w\\d\\s]", "").trim().toLowerCase();
		if (groupingAuthor.length() > 50){
			groupingAuthor = groupingAuthor.substring(0, 50);
		}
		return groupingAuthor;
	}


	private String normalizeSubtitle(String originalTitle) {
		String groupingSubtitle = originalTitle.replaceAll("&", "and");
		groupingSubtitle = groupingSubtitle.replaceAll("[^\\w\\d\\s]", "").toLowerCase();
		if (groupingSubtitle.length() > 175){
			groupingSubtitle = groupingSubtitle.substring(0, 175);
		}
		groupingSubtitle = groupingSubtitle.trim();
		return groupingSubtitle;
	}


	private String normalizeTitle(String fullTitle, int numNonFilingCharacters) {
		String groupingTitle;
		if (numNonFilingCharacters > 0 && numNonFilingCharacters < fullTitle.length()){
			groupingTitle = fullTitle.substring(numNonFilingCharacters);
		}else{
			groupingTitle = fullTitle;
		}

		//Replace & with and for better matching
		groupingTitle = groupingTitle.replace("&", "and");
		groupingTitle = groupingTitle.replaceAll("[^\\w\\d\\s]", "").toLowerCase();
		groupingTitle = groupingTitle.trim();

		int titleEnd = 100;
		if (titleEnd < groupingTitle.length()) {
			groupingTitle = groupingTitle.substring(0, titleEnd);
		}
		return groupingTitle;
	}

	public long getFuzzyMatchFromCatalog(GroupedWork originalRecord) throws SQLException {
		//Get a fuzzy match from the catalog.
		//Check identifiers
		long returnRecordId = -1;
		for (RecordIdentifier recordIdentifier : originalRecord.identifiers){
			getGroupedWorksForIdentifierStmt.setString(1, recordIdentifier.type);
			getGroupedWorksForIdentifierStmt.setString(2, recordIdentifier.identifier);
			getGroupedWorksForIdentifierStmt.setString(3, originalRecord.groupingCategory);
			ResultSet recordsForIdentifier = getGroupedWorksForIdentifierStmt.executeQuery();
			//Check to see how many matches we got.
			if (recordsForIdentifier.next()){
				recordsForIdentifier.last();
				int numMatches = recordsForIdentifier.getRow();
				if (numMatches == 1){
					//We got a good match!
					Long groupedRecordId = recordsForIdentifier.getLong("id");
					String title = recordsForIdentifier.getString("title");
					String author = recordsForIdentifier.getString("author");
					if (!compareStrings(title, originalRecord.title)){
						System.out.println("Found match by identifier, but title did not match, marking as linking to different titles.  " + recordIdentifier.type + ": " + recordIdentifier.identifier);
						System.out.println("  '" + title + "' != '" + originalRecord.title + "'");
						updateLinksToDifferentTitlesForIdentifier.setString(1, recordIdentifier.type);
						updateLinksToDifferentTitlesForIdentifier.setString(2, recordIdentifier.identifier);
						updateLinksToDifferentTitlesForIdentifier.executeUpdate();
					} else if (!compareStrings(author, originalRecord.author)){
						System.out.println("Found match by identifier, but author did not match, marking as linking to different titles.  " + recordIdentifier.type + ": " + recordIdentifier.identifier);
						System.out.println("  '" + author + "' != '" + originalRecord.author + "'");
						updateLinksToDifferentTitlesForIdentifier.setString(1, recordIdentifier.type);
						updateLinksToDifferentTitlesForIdentifier.setString(2, recordIdentifier.identifier);
						updateLinksToDifferentTitlesForIdentifier.executeUpdate();
					}else{
						//This seems to be a good match
						returnRecordId = groupedRecordId;
					}
				}else{
					//Hmm, there are multiple records based on ISBN.  Check more stuff
					System.out.println("Multiple grouped records found for identifier " + recordIdentifier.type + " " + recordIdentifier.identifier + " found " + numMatches);
				}
			}
			recordsForIdentifier.close();
			if (returnRecordId != -1){
				break;
			}
		}
		return returnRecordId;
	}

	public void processMarcRecord(Record marcRecord){
		GroupedWork groupedWork = getGroupedWorkForMarc(marcRecord);
		addGroupedWorkToDatabase(groupedWork);
	}

	private void addGroupedWorkToDatabase(GroupedWork groupedWork) {
		String groupedWorkPermanentId = groupedWork.getPermanentId();
		long groupedWorkId;
		try{
			getGroupedWorkStmt.setString(1, groupedWorkPermanentId);
			ResultSet groupedWorkRS = getGroupedWorkStmt.executeQuery();
			if (groupedWorkRS.next()){
				//There is an existing grouped record
				groupedWorkId = groupedWorkRS.getLong(1);
				groupedWorkRS.close();
			}else{
				groupedWorkRS.close();
				//Check to see if we can get a match by rotating author names since some sources
				//  put authors first name first and some do last name first
				groupedWorkId = findWorkWithRotatedAuthorName(groupedWork);

				//Look for matches based on identifiers
				if (groupedWorkId == -1){
					groupedWorkId = getFuzzyMatchFromCatalog(groupedWork);
				}

				//Look for the work based on subtitles
				if (groupedWorkId == -1){
					groupedWorkId = findWorkWithSubtitleVariations(groupedWork);
				}

				if (groupedWorkId == -1){
					//Need to insert a new grouped record
					insertGroupedWorkStmt.setString(1, groupedWork.title);
					insertGroupedWorkStmt.setString(2, groupedWork.subtitle);
					insertGroupedWorkStmt.setString(3, groupedWork.author);
					insertGroupedWorkStmt.setString(4, groupedWork.groupingCategory);
					insertGroupedWorkStmt.setString(5, groupedWorkPermanentId);

					insertGroupedWorkStmt.executeUpdate();
					ResultSet generatedKeysRS = insertGroupedWorkStmt.getGeneratedKeys();
					if (generatedKeysRS.next()){
						groupedWorkId = generatedKeysRS.getLong(1);
					}
				}
			}

			//Update identifiers
			addIdentifiersForRecordToDB(groupedWorkId, groupedWork.identifiers);
		}catch (Exception e){
			System.out.println("Error adding grouped record to grouped work " + e.toString());
			e.printStackTrace();
		}
	}

	private long findWorkWithSubtitleVariations(GroupedWork groupedWork) throws SQLException {
		long groupedWorkId = -1;
		if (groupedWork.subtitle.length() == 0){
			//Check to see if there is a record in the database that matches the title, author, and grouping category even if it has a subtitle
			getRecordIgnoringSubfieldStmt.setString(1, groupedWork.title);
			getRecordIgnoringSubfieldStmt.setString(2, groupedWork.author);
			getRecordIgnoringSubfieldStmt.setString(3, groupedWork.groupingCategory);
			ResultSet recordIgnoringSubfieldRS = getRecordIgnoringSubfieldStmt.executeQuery();
			if (recordIgnoringSubfieldRS.next()){
				groupedWorkId = recordIgnoringSubfieldRS.getLong("id");
				System.out.println("Found a record to match that did have a subfield even though this record didn't.  " + groupedWork.title + " : " + groupedWork.author);
			}
			recordIgnoringSubfieldRS.close();
		}else{
			//Check to see if there is a record in the database that doesn't have a subtitle yet
			getRecordWithoutSubfieldStmt.setString(1, groupedWork.title);
			getRecordWithoutSubfieldStmt.setString(2, groupedWork.author);
			getRecordWithoutSubfieldStmt.setString(3, groupedWork.groupingCategory);
			ResultSet recordWithoutSubfieldRS = getRecordWithoutSubfieldStmt.executeQuery();
			if (recordWithoutSubfieldRS.next()){
				groupedWorkId = recordWithoutSubfieldRS.getLong("id");
				System.out.println("Found a record to match that did not have a subfield even though this record does.  " + groupedWork.title + " : " + groupedWork.author);
				//update the record to include the subtitle
				updateRecordSubtitleStmt.setString(1, groupedWork.getPermanentId());
				updateRecordSubtitleStmt.setString(2, groupedWork.subtitle);
				updateRecordSubtitleStmt.setLong(3, groupedWorkId);
				updateRecordSubtitleStmt.executeUpdate();
			}
			recordWithoutSubfieldRS.close();
		}
		return groupedWorkId;
	}


	private long findWorkWithRotatedAuthorName(GroupedWork groupedWork) throws SQLException {
		long groupedWorkId = -1;
		if (groupedWork.author.indexOf(' ') > 0){
			String[] authorParts = groupedWork.author.split("\\s");
			if (authorParts.length == 2){
				String newAuthor = authorParts[1] + " " + authorParts[0];
				GroupedWork tempGroupWork = groupedWork.clone();
				tempGroupWork.author = newAuthor;
				getGroupedWorkStmt.setString(1, tempGroupWork.getPermanentId());
				ResultSet groupedWorkRS2 = getGroupedWorkStmt.executeQuery();
				if (groupedWorkRS2.next()){
					//There is an existing grouped record
					groupedWorkId = groupedWorkRS2.getLong(1);
					System.out.println("Grouped Record by rotating author names");
				}
			}
		}
		return groupedWorkId;
	}

	private void addIdentifiersForRecordToDB(long groupedWorkId, HashSet<RecordIdentifier> identifiers) throws SQLException {
		getGroupedWorkIdentifiersStmt.setLong(1, groupedWorkId);
		ResultSet existingIdentifiersRS = getGroupedWorkIdentifiersStmt.executeQuery();
		HashSet<RecordIdentifier> existingIdentifiers = new HashSet<RecordIdentifier>();
		while (existingIdentifiersRS.next()){
			RecordIdentifier existingIdentifier = new RecordIdentifier();
			existingIdentifier.type = existingIdentifiersRS.getString("type");
			existingIdentifier.identifier = existingIdentifiersRS.getString("identifier");
			existingIdentifiers.add(existingIdentifier);
		}

		for (RecordIdentifier curIdentifier :  identifiers){
			if (!existingIdentifiers.contains(curIdentifier)){
				addIdentifierToGroupedWorkStmt.setLong(1, groupedWorkId);
				addIdentifierToGroupedWorkStmt.setString(2, curIdentifier.type);
				addIdentifierToGroupedWorkStmt.setString(3, curIdentifier.identifier);
				try{
					addIdentifierToGroupedWorkStmt.executeUpdate();
				}catch (SQLException e){
					System.out.println("Duplicate identifiers found");
					for (RecordIdentifier curIdentifier2 :  identifiers){
						System.out.println("   '" + curIdentifier2.toString() + "'");
					}
				}
			}
		}
	}

	public void processRecord(String title, String subtitle, String author, String format, HashSet<RecordIdentifier>identifiers){
		GroupedWork groupedWork = new GroupedWork();

		//Replace & with and for better matching
		if (title != null){
			groupedWork.title = normalizeTitle(title, 0);
		}

		if (subtitle != null){
			groupedWork.subtitle = normalizeSubtitle(subtitle);
		}

		if (author != null){
			groupedWork.author = normalizeAuthor(author);
		}

		if (format.equalsIgnoreCase("audiobook")){
			groupedWork.groupingCategory = "book";
		}else if (format.equalsIgnoreCase("ebook")){
			groupedWork.groupingCategory = "book";
		}else if (format.equalsIgnoreCase("music")){
			groupedWork.groupingCategory = "music";
		}else if (format.equalsIgnoreCase("video")){
			groupedWork.groupingCategory = "video";
		}

		groupedWork.identifiers = identifiers;

		addGroupedWorkToDatabase(groupedWork);
	}

	private String getFormat(Record record) {
		//Check to see if the title is eContent based on the 989 field
		List<DataField> itemFields = getDataFields(record, "989");
		for (DataField itemField : itemFields){
			if (itemField.getSubfield('w') != null){
				//The record is some type of eContent.  For this purpose, we don't care what type
				return "eContent";
			}
		}

		String leader = record.getLeader().toString();
		char leaderBit;
		ControlField fixedField = (ControlField) record.getVariableField("008");
		char formatCode;

		// check for music recordings quickly so we can figure out if it is music
		// for category (need to do here since checking what is on the Compact
		// Disc/Phonograph, etc is difficult).
		if (leader.length() >= 6) {
			leaderBit = leader.charAt(6);
			switch (Character.toUpperCase(leaderBit)) {
				case 'J':
					return "MusicRecording";
			}
		}

		// check for playaway in 260|b
		DataField sysDetailsNote = (DataField) record.getVariableField("260");
		if (sysDetailsNote != null) {
			if (sysDetailsNote.getSubfield('b') != null) {
				String sysDetailsValue = sysDetailsNote.getSubfield('b').getData().toLowerCase();
				if (sysDetailsValue.contains("playaway")) {
					return "Playaway";
				}
			}
		}

		// Check for formats in the 538 field
		DataField sysDetailsNote2 = (DataField) record.getVariableField("538");
		if (sysDetailsNote2 != null) {
			if (sysDetailsNote2.getSubfield('a') != null) {
				String sysDetailsValue = sysDetailsNote2.getSubfield('a').getData().toLowerCase();
				if (sysDetailsValue.contains("playaway")) {
					return "Playaway";
				} else if (sysDetailsValue.contains("bluray")
						|| sysDetailsValue.contains("blu-ray")) {
					return "Blu-ray";
				} else if (sysDetailsValue.contains("dvd")) {
					return "DVD";
				} else if (sysDetailsValue.contains("vertical file")) {
					return "VerticalFile";
				}
			}
		}

		// Check for formats in the 500 tag
		DataField noteField = (DataField) record.getVariableField("500");
		if (noteField != null) {
			if (noteField.getSubfield('a') != null) {
				String noteValue = noteField.getSubfield('a').getData().toLowerCase();
				if (noteValue.contains("vertical file")) {
					return "VerticalFile";
				}
			}
		}

		// Check for large print book (large format in 650, 300, or 250 fields)
		// Check for blu-ray in 300 fields
		DataField edition = (DataField) record.getVariableField("250");
		if (edition != null) {
			if (edition.getSubfield('a') != null) {
				if (edition.getSubfield('a').getData().toLowerCase().contains("large type")) {
					return "LargePrint";
				}
			}
		}

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
						return "LargePrint";
					} else if (subfield.getData().toLowerCase().contains("bluray")
							|| subfield.getData().toLowerCase().contains("blu-ray")) {
						return "Blu-ray";
					}
				}
			}
		}
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
						return "LargePrint";
					}
				}
			}
		}

		List<DataField> localTopicalTerm = getDataFields(record, "690");
		if (localTopicalTerm != null) {
			Iterator<DataField> fieldsIterator = localTopicalTerm.iterator();
			DataField field;
			while (fieldsIterator.hasNext()) {
				field = fieldsIterator.next();
				Subfield subfieldA = field.getSubfield('a');
				if (subfieldA != null) {
					if (subfieldA.getData().toLowerCase().contains("seed library")) {
						return "SeedPacket";
					}
				}
			}
		}

		// check the 007 - this is a repeating field
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
								return "Atlas";
							default:
								return "Map";
						}
					case 'C':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'A':
								return "TapeCartridge";
							case 'B':
								return "ChipCartridge";
							case 'C':
								return "DiscCartridge";
							case 'F':
								return "TapeCassette";
							case 'H':
								return "TapeReel";
							case 'J':
								return "FloppyDisk";
							case 'M':
							case 'O':
								return "CDROM";
							case 'R':
								// Do not return - this will cause anything with an
								// 856 field to be labeled as "Electronic"
								break;
							default:
								return "Software";
						}
						break;
					case 'D':
						return "Globe";
					case 'F':
						return "Braille";
					case 'G':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'C':
							case 'D':
								return "Filmstrip";
							case 'T':
								return "Transparency";
							default:
								return "Slide";
						}
					case 'H':
						return "Microfilm";
					case 'K':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'C':
								return "Collage";
							case 'D':
								return "Drawing";
							case 'E':
								return "Painting";
							case 'F':
								return "Print";
							case 'G':
								return "Photonegative";
							case 'J':
								return "Print";
							case 'L':
								return "Drawing";
							case 'O':
								return "FlashCard";
							case 'N':
								return "Chart";
							default:
								return "Photo";
						}
					case 'M':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'F':
								return "VideoCassette";
							case 'R':
								return "Filmstrip";
							default:
								return "MotionPicture";
						}
					case 'O':
						return "Kit";
					case 'Q':
						return "MusicalScore";
					case 'R':
						return "SensorImage";
					case 'S':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'D':
								if (formatField.getData().length() >= 4) {
									char speed = formatField.getData().toUpperCase().charAt(3);
									if (speed >= 'A' && speed <= 'E') {
										return "Phonograph";
									} else if (speed == 'F') {
										return "CompactDisc";
									} else if (speed >= 'K' && speed <= 'R') {
										return "TapeRecording";
									} else {
										return "SoundDisc";
									}
								} else {
									return "SoundDisc";
								}
							case 'S':
								return "SoundCassette";
							default:
								return "SoundRecording";
						}
					case 'T':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'A':
								return "Book";
							case 'B':
								return "LargePrint";
						}
					case 'V':
						switch (formatField.getData().toUpperCase().charAt(1)) {
							case 'C':
								return "VideoCartridge";
							case 'D':
								return "VideoDisc";
							case 'F':
								return "VideoCassette";
							case 'R':
								return "VideoReel";
							default:
								return "Video";
						}
				}
			}
		}

		// check the Leader at position 6
		if (leader.length() >= 6) {
			leaderBit = leader.charAt(6);
			switch (Character.toUpperCase(leaderBit)) {
				case 'C':
				case 'D':
					return "MusicalScore";
				case 'E':
				case 'F':
					return "Map";
				case 'G':
					// We appear to have a number of items without 007 tags marked as G's.
					// These seem to be Videos rather than Slides.
					// return "Slide");
					return "Video";
				case 'I':
					return "SoundRecording";
				case 'J':
					return "MusicRecording";
				case 'K':
					return "Photo";
				case 'M':
					return "Electronic";
				case 'O':
				case 'P':
					return "Kit";
				case 'R':
					return "PhysicalObject";
				case 'T':
					return "Manuscript";
			}
		}

		if (leader.length() >= 7) {
			// check the Leader at position 7
			leaderBit = leader.charAt(7);
			switch (Character.toUpperCase(leaderBit)) {
				// Monograph
				case 'M':
					return "Book";
				// Serial
				case 'S':
					// Look in 008 to determine what type of Continuing Resource
					if (fixedField != null){
						formatCode = fixedField.getData().toUpperCase().charAt(21);
						switch (formatCode) {
							case 'N':
								return "Newspaper";
							case 'P':
								return "Journal";
							default:
								return "Serial";
						}
					}
			}
		}

		// Nothing worked!
		return "Unknown";
	}

	public static String convertISBN10to13(String isbn10){
		if (isbn10.length() != 10){
			return null;
		}
		String isbn = "978" + isbn10.substring(0, 9);
		//Calculate the 13 digit checksum
		int sumOfDigits = 0;
		for (int i = 0; i < 12; i++){
			int multiplier = 1;
			if (i % 2 == 1){
				multiplier = 3;
			}
			sumOfDigits += multiplier * (int)(isbn.charAt(i));
		}
		int modValue = sumOfDigits % 10;
		int checksumDigit;
		if (modValue == 0){
			checksumDigit = 0;
		}else{
			checksumDigit = 10 - modValue;
		}
		return  isbn + Integer.toString(checksumDigit);
	}

	public static boolean compareStrings(String catalogValue, String importValue) {
		if (catalogValue == null){
			//If we have a value in the import, but not the catalog that's ok.
			return true;
		}else if (importValue == null){
			//If we have a value in the catalog, but not the import, that's ok
			return true;
		}else{
			if (catalogValue.equals(importValue) || catalogValue.startsWith(importValue) || importValue.startsWith(catalogValue) || catalogValue.endsWith(importValue) || importValue.endsWith(catalogValue)){
				//Got a good match
				return true;
			}else{
				//Match without spaces since sometimes we get 1 2 3 in one catalog and 123 in another
				String catalogValueNoSpaces = catalogValue.replace(" ", "");
				String importValueNoSpaces = importValue.replace(" ", "");
				if (catalogValueNoSpaces.equals(importValueNoSpaces) || catalogValueNoSpaces.startsWith(importValueNoSpaces) || importValue.startsWith(catalogValue) || catalogValueNoSpaces.endsWith(importValueNoSpaces) || importValue.endsWith(catalogValue)){
					System.out.println("Matched string when spaces were removed.");
					//Got a good match
					return true;
				}
				return false;
			}
		}
	}

	private static HashMap<String, String> formatsToGroupingCategory = new HashMap<String, String>();
	static {
		formatsToGroupingCategory.put("Atlas", "other");
		formatsToGroupingCategory.put("Map", "other");
		formatsToGroupingCategory.put("TapeCartridge", "other");
		formatsToGroupingCategory.put("ChipCartridge", "other");
		formatsToGroupingCategory.put("DiscCartridge", "other");
		formatsToGroupingCategory.put("TapeCassette", "other");
		formatsToGroupingCategory.put("TapeReel", "other");
		formatsToGroupingCategory.put("FloppyDisk", "other");
		formatsToGroupingCategory.put("CDROM", "other");
		formatsToGroupingCategory.put("Software", "other");
		formatsToGroupingCategory.put("Globe", "other");
		formatsToGroupingCategory.put("Braille", "book");
		formatsToGroupingCategory.put("Filmstrip", "movie");
		formatsToGroupingCategory.put("Transparency", "other");
		formatsToGroupingCategory.put("Slide", "other");
		formatsToGroupingCategory.put("Microfilm", "other");
		formatsToGroupingCategory.put("Collage", "other");
		formatsToGroupingCategory.put("Drawing", "other");
		formatsToGroupingCategory.put("Painting", "other");
		formatsToGroupingCategory.put("Print", "other");
		formatsToGroupingCategory.put("Photonegative", "other");
		formatsToGroupingCategory.put("FlashCard", "other");
		formatsToGroupingCategory.put("Chart", "other");
		formatsToGroupingCategory.put("Photo", "other");
		formatsToGroupingCategory.put("MotionPicture", "movie");
		formatsToGroupingCategory.put("Kit", "other");
		formatsToGroupingCategory.put("MusicalScore", "book");
		formatsToGroupingCategory.put("SensorImage", "other");
		formatsToGroupingCategory.put("SoundDisc", "audio");
		formatsToGroupingCategory.put("SoundCassette", "audio");
		formatsToGroupingCategory.put("SoundRecording", "audio");
		formatsToGroupingCategory.put("VideoCartridge", "movie");
		formatsToGroupingCategory.put("VideoDisc", "movie");
		formatsToGroupingCategory.put("VideoCassette", "movie");
		formatsToGroupingCategory.put("VideoReel", "movie");
		formatsToGroupingCategory.put("Video", "movie");
		formatsToGroupingCategory.put("MusicalScore", "book");
		formatsToGroupingCategory.put("MusicRecording", "music");
		formatsToGroupingCategory.put("Electronic", "other");
		formatsToGroupingCategory.put("PhysicalObject", "other");
		formatsToGroupingCategory.put("Manuscript", "book");
		formatsToGroupingCategory.put("eBook", "ebook");
		formatsToGroupingCategory.put("Book", "book");
		formatsToGroupingCategory.put("Newspaper", "book");
		formatsToGroupingCategory.put("Journal", "book");
		formatsToGroupingCategory.put("Serial", "book");
		formatsToGroupingCategory.put("Unknown", "other");
		formatsToGroupingCategory.put("Playaway", "audio");
		formatsToGroupingCategory.put("LargePrint", "book");
		formatsToGroupingCategory.put("Blu-ray", "movie");
		formatsToGroupingCategory.put("DVD", "movie");
		formatsToGroupingCategory.put("VerticalFile", "other");
		formatsToGroupingCategory.put("CompactDisc", "audio");
		formatsToGroupingCategory.put("TapeRecording", "audio");
		formatsToGroupingCategory.put("Phonograph", "audio");
		formatsToGroupingCategory.put("pdf", "ebook");
		formatsToGroupingCategory.put("epub", "ebook");
		formatsToGroupingCategory.put("jpg", "other");
		formatsToGroupingCategory.put("gif", "other");
		formatsToGroupingCategory.put("mp3", "audio");
		formatsToGroupingCategory.put("plucker", "ebook");
		formatsToGroupingCategory.put("kindle", "ebook");
		formatsToGroupingCategory.put("externalLink", "ebook");
		formatsToGroupingCategory.put("externalMP3", "audio");
		formatsToGroupingCategory.put("interactiveBook", "ebook");
		formatsToGroupingCategory.put("overdrive", "ebook");
		formatsToGroupingCategory.put("external_web", "ebook");
		formatsToGroupingCategory.put("external_ebook", "ebook");
		formatsToGroupingCategory.put("external_eaudio", "audio");
		formatsToGroupingCategory.put("external_emusic", "music");
		formatsToGroupingCategory.put("external_evideo", "movie");
		formatsToGroupingCategory.put("text", "ebook");
		formatsToGroupingCategory.put("gifs", "other");
		formatsToGroupingCategory.put("itunes", "audio");
		formatsToGroupingCategory.put("Adobe_EPUB_eBook", "ebook");
		formatsToGroupingCategory.put("Kindle_Book", "ebook");
		formatsToGroupingCategory.put("Microsoft_eBook", "ebook");
		formatsToGroupingCategory.put("OverDrive_WMA_Audiobook", "audio");
		formatsToGroupingCategory.put("OverDrive_MP3_Audiobook", "audio");
		formatsToGroupingCategory.put("OverDrive_Music", "music");
		formatsToGroupingCategory.put("OverDrive_Video", "movie");
		formatsToGroupingCategory.put("OverDrive_Read", "ebook");
		formatsToGroupingCategory.put("Adobe_PDF_eBook", "ebook");
		formatsToGroupingCategory.put("Palm", "ebook");
		formatsToGroupingCategory.put("Mobipocket_eBook", "ebook");
		formatsToGroupingCategory.put("Disney_Online_Book", "ebook");
		formatsToGroupingCategory.put("Open_PDF_eBook", "ebook");
		formatsToGroupingCategory.put("Open_EPUB_eBook", "ebook");
		formatsToGroupingCategory.put("eContent", "ebook");
		formatsToGroupingCategory.put("SeedPacket", "other");
	}

	private static HashMap<String, String> categoryMap = new HashMap<String, String>();
	static {
		categoryMap.put("other", "book");
		categoryMap.put("book", "book");
		categoryMap.put("ebook", "book");
		categoryMap.put("audio", "book");
		categoryMap.put("music", "music");
		categoryMap.put("movie", "movie");
	}



}
