package org.pika;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.ini4j.Profile.Section;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.codec.binary.Base64;
import org.marc4j.*;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

/**
 * Export data to
 * Pika
 * User: Mark Noble
 * Date: 1/15/18
 */
public class SierraExportAPIMain {
	private static Logger logger = Logger.getLogger(SierraExportAPIMain.class);
	private static String serverName;

	private static IndexingProfile    indexingProfile;
	private static GroupedWorkIndexer groupedWorkIndexer;
	private static MarcRecordGrouper  recordGroupingProcessor;

	private static Long    lastSierraExtractTime           = null;
	private static Long    lastSierraExtractTimeVariableId = null;
	private static String  apiBaseUrl                      = null;
	private static boolean allowFastExportMethod           = true;
	private static boolean exportItemHolds                 = true;
	private static Ini     ini;


	private static TreeSet<String> allBibsToUpdate = new TreeSet<>();
	private static TreeSet<String> allDeletedIds   = new TreeSet<>();
	private static TreeSet<String> bibsWithErrors  = new TreeSet<>();

	//Reporting information
	private static long              exportLogId;
	private static PreparedStatement addNoteToExportLogStmt;
	private static String            exportPath;
	private static boolean           debug = false;

	//Temporary
	private static char bibLevelLocationsSubfield = 'a'; //TODO: may need to make bib -level locations field an indexing setting


	public static void main(String[] args) {
		serverName = args[0];

		Date startTime = new Date();
		File log4jFile = new File("../../sites/" + serverName + "/conf/log4j.sierra_extract.properties");
		if (log4jFile.exists()) {
			PropertyConfigurator.configure(log4jFile.getAbsolutePath());
		} else {
			logger.error("Could not find log4j configuration " + log4jFile.toString());
		}
		logger.info(startTime.toString() + ": Starting Sierra Extract");

		// Read the base INI file to get information about the server (current directory/cron/config.ini)
		ini = loadConfigFile("config.ini");
		String exportItemHoldsStr = cleanIniValue(ini.get("Catalog", "exportItemHolds"));
		if (exportItemHoldsStr != null) {
			exportItemHolds = exportItemHoldsStr.equalsIgnoreCase("true") || exportItemHoldsStr.equals("1");
		}
		String debugStr = cleanIniValue(ini.get("System", "debug"));
		if (debugStr != null) {
			debug = debugStr.equalsIgnoreCase("true") || debugStr.equals("1");
		}

		//Connect to the pika database
		Connection pikaConn = null;
		try {
			String databaseConnectionInfo = cleanIniValue(ini.get("Database", "database_vufind_jdbc"));
			if (databaseConnectionInfo != null) {
				pikaConn = DriverManager.getConnection(databaseConnectionInfo);
			} else {
				logger.error("No Pika database connection info");
				System.exit(2); // Exiting with a status code of 2 so that our executing bash scripts knows there has been a database communication error
			}
		} catch (Exception e) {
			logger.error("Error connecting to Pika database " + e.toString());
			System.exit(2); // Exiting with a status code of 2 so that our executing bash scripts knows there has been a database communication error
		}
		//Connect to the pika database
		Connection econtentConn = null;
		try {
			String databaseConnectionInfo = cleanIniValue(ini.get("Database", "database_econtent_jdbc"));
			if (databaseConnectionInfo != null) {
				econtentConn = DriverManager.getConnection(databaseConnectionInfo);
			} else {
				logger.error("No eContent database connection info");
				System.exit(2); // Exiting with a status code of 2 so that our executing bash scripts knows there has been a database communication error
			}
		} catch (Exception e) {
			System.out.println("Error connecting to econtent database " + e.toString());
			System.exit(2); // Exiting with a status code of 2 so that our executing bash scripts knows there has been a database communication error
		}

		String profileToLoad = "ils";
		if (args.length > 1) {
			profileToLoad = args[1];
		}
		indexingProfile = IndexingProfile.loadIndexingProfile(pikaConn, profileToLoad, logger);


		//Start an export log entry
		logger.info("Creating log entry for index");
		try (PreparedStatement createLogEntryStatement = pikaConn.prepareStatement("INSERT INTO sierra_api_export_log (startTime, lastUpdate, notes) VALUES (?, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
			createLogEntryStatement.setLong(1, startTime.getTime() / 1000);
			createLogEntryStatement.setLong(2, startTime.getTime() / 1000);
			createLogEntryStatement.setString(3, "Initialization complete");
			createLogEntryStatement.executeUpdate();
			ResultSet generatedKeys = createLogEntryStatement.getGeneratedKeys();
			if (generatedKeys.next()) {
				exportLogId = generatedKeys.getLong(1);
			}

			addNoteToExportLogStmt = pikaConn.prepareStatement("UPDATE sierra_api_export_log SET notes = ?, lastUpdate = ? WHERE id = ?");
		} catch (SQLException e) {
			logger.error("Unable to create log entry for record grouping process", e);
			System.exit(0);
		}

		exportPath = indexingProfile.marcPath;
		File changedBibsFile = new File(exportPath + "/changed_bibs_to_process.csv");

		// Inititalize Reindexer (used in deleteRecord() )
		groupedWorkIndexer = new GroupedWorkIndexer(serverName, pikaConn, econtentConn, ini, false, false, logger);

		//Process MARC record changes
		getBibsAndItemUpdatesFromSierra(ini, pikaConn, changedBibsFile);

		//Write the number of updates to the log
		try (PreparedStatement setNumProcessedStmt = pikaConn.prepareStatement("UPDATE sierra_api_export_log SET numRecordsToProcess = ? WHERE id = ?", PreparedStatement.RETURN_GENERATED_KEYS)) {
			setNumProcessedStmt.setLong(1, allBibsToUpdate.size());
			setNumProcessedStmt.setLong(2, exportLogId);
			setNumProcessedStmt.executeUpdate();
		} catch (SQLException e) {
			logger.error("Unable to update log entry with number of records that have changed", e);
		}

		//Connect to the sierra database
		String url              = cleanIniValue(ini.get("Catalog", "sierra_db"));
		String sierraDBUser     = cleanIniValue(ini.get("Catalog", "sierra_db_user"));
		String sierraDBPassword = cleanIniValue(ini.get("Catalog", "sierra_db_password"));

		Connection conn = null;
		if (url != null) {
			try {
				//Open the connection to the database
				if (sierraDBUser != null && sierraDBPassword != null && !sierraDBPassword.isEmpty() && !sierraDBUser.isEmpty()) {
					// Use specific user name and password when the are issues with special characters
					conn = DriverManager.getConnection(url, sierraDBUser, sierraDBPassword);
				} else {
					// This version assumes user name and password are supplied in the url
					conn = DriverManager.getConnection(url);
				}

				exportActiveOrders(exportPath, conn);
				exportDueDates(exportPath, conn);
				exportHolds(conn, pikaConn);

			} catch (Exception e) {
				System.out.println("Error: " + e.toString());
				e.printStackTrace();
			}
		}

		try (PreparedStatement setNumProcessedStmt = pikaConn.prepareStatement("UPDATE sierra_api_export_log SET numRecordsToProcess = ? WHERE id = ?", PreparedStatement.RETURN_GENERATED_KEYS)) {
			setNumProcessedStmt.setLong(1, allBibsToUpdate.size());
			setNumProcessedStmt.setLong(2, exportLogId);
			setNumProcessedStmt.executeUpdate();
		} catch (SQLException e) {
			logger.error("Unable to update log entry with number of records that have changed", e);
		}

		//Setup other systems we will use
		recordGroupingProcessor = new MarcRecordGrouper(pikaConn, indexingProfile, logger, false);

		int numRecordsProcessed = updateBibs(ini);

		//Write any records that still haven't been processed

		try (BufferedWriter itemsToProcessWriter = new BufferedWriter(new FileWriter(changedBibsFile, false))) {
			for (String bibToUpdate : allBibsToUpdate) {
				itemsToProcessWriter.write(bibToUpdate + "\r\n");
			}
			//Write any bibs that had errors
			for (String bibToUpdate : bibsWithErrors) {
				itemsToProcessWriter.write(bibToUpdate + "\r\n");
			}
			itemsToProcessWriter.flush();
			itemsToProcessWriter.close();

		} catch (Exception e) {
			logger.error("Error saving remaining bibs to process", e);
		}

		//Write stats to the log
		try (PreparedStatement setNumProcessedStmt = pikaConn.prepareStatement("UPDATE sierra_api_export_log SET numRecordsProcessed = ?, numErrors = ?, numRemainingRecords =? WHERE id = ?", PreparedStatement.RETURN_GENERATED_KEYS)) {
			setNumProcessedStmt.setLong(1, numRecordsProcessed);
			setNumProcessedStmt.setLong(2, bibsWithErrors.size());
			setNumProcessedStmt.setLong(3, allBibsToUpdate.size());
			setNumProcessedStmt.setLong(4, exportLogId);
			setNumProcessedStmt.executeUpdate();

		} catch (SQLException e) {
			logger.error("Unable to update log entry with final stats", e);
		}

		updateLastExportTime(pikaConn, startTime.getTime() / 1000);
		addNoteToExportLog("Setting last export time to " + (startTime.getTime() / 1000));

		addNoteToExportLog("Finished exporting sierra data " + new Date().toString());
		long endTime     = new Date().getTime();
		long elapsedTime = endTime - startTime.getTime();
		addNoteToExportLog("Elapsed Minutes " + (elapsedTime / 60000));

		try {
			PreparedStatement finishedStatement = pikaConn.prepareStatement("UPDATE sierra_api_export_log SET endTime = ? WHERE id = ?");
			finishedStatement.setLong(1, endTime / 1000);
			finishedStatement.setLong(2, exportLogId);
			finishedStatement.executeUpdate();
		} catch (SQLException e) {
			logger.error("Unable to update sierra api export log with completion time.", e);
		}

		if (conn != null) {
			try {
				//Close the connection
				conn.close();
			} catch (Exception e) {
				System.out.println("Error closing connection: " + e.toString());
				e.printStackTrace();
			}
		}

		try {
			//Close the connection
			pikaConn.close();
		} catch (Exception e) {
			System.out.println("Error closing connection: " + e.toString());
			e.printStackTrace();
		}
		Date currentTime = new Date();
		logger.info(currentTime.toString() + ": Finished Sierra Extract");
	}

	private static void updateLastExportTime(Connection pikaConn, long exportStartTime) {
		try {
			//Update the last extract time
			if (lastSierraExtractTimeVariableId != null) {
				PreparedStatement updateVariableStmt = pikaConn.prepareStatement("UPDATE variables set value = ? WHERE id = ?");
				updateVariableStmt.setLong(1, exportStartTime);
				updateVariableStmt.setLong(2, lastSierraExtractTimeVariableId);
				updateVariableStmt.executeUpdate();
				updateVariableStmt.close();
			} else {
				PreparedStatement insertVariableStmt = pikaConn.prepareStatement("INSERT INTO variables (`name`, `value`) VALUES ('last_sierra_extract_time', ?)");
				insertVariableStmt.setString(1, Long.toString(exportStartTime));
				insertVariableStmt.executeUpdate();
				insertVariableStmt.close();
			}
			PreparedStatement setRemainingRecordsStmt = pikaConn.prepareStatement("INSERT INTO variables (`name`, `value`) VALUES ('remaining_sierra_records', ?) ON DUPLICATE KEY UPDATE value=VALUES(value)");
			setRemainingRecordsStmt.setString(1, "0");
			setRemainingRecordsStmt.executeUpdate();
			setRemainingRecordsStmt.close();
		} catch (Exception e) {
			logger.error("There was an error updating the database, not setting last extract time.", e);
		}
	}

	private static void getBibsAndItemUpdatesFromSierra(Ini ini, Connection pikaConn, File changedBibsFile) {
		//Load unprocessed transactions
		try {
			if (changedBibsFile.exists()) {
				BufferedReader changedBibsReader = new BufferedReader(new FileReader(changedBibsFile));
				String         curLine           = changedBibsReader.readLine();
				while (curLine != null) {
					allBibsToUpdate.add(curLine);
					curLine = changedBibsReader.readLine();
				}
				changedBibsReader.close();
			}
		} catch (Exception e) {
			logger.error("Error loading changed bibs to process");
		}

		try (PreparedStatement loadLastSierraExtractTimeStmt = pikaConn.prepareStatement("SELECT * from variables WHERE name = 'last_sierra_extract_time'", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			 ResultSet lastSierraExtractTimeRS = loadLastSierraExtractTimeStmt.executeQuery()) {
			if (lastSierraExtractTimeRS.next()) {
				lastSierraExtractTime           = lastSierraExtractTimeRS.getLong("value");
				lastSierraExtractTimeVariableId = lastSierraExtractTimeRS.getLong("id");
			}
		} catch (Exception e) {
			logger.error("Unable to load last_sierra_extract_time from variables", e);
			return;
		}

		try (PreparedStatement allowFastExportMethodStmt = pikaConn.prepareStatement("SELECT * from variables WHERE name = 'allow_sierra_fast_export'", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			 ResultSet allowFastExportMethodRS = allowFastExportMethodStmt.executeQuery()) {
			if (allowFastExportMethodRS.next()) {
				allowFastExportMethod = allowFastExportMethodRS.getBoolean("value");
			} else {
				pikaConn.prepareStatement("INSERT INTO variables (name, value) VALUES ('allow_sierra_fast_export', 1)").executeUpdate();
			}
		} catch (Exception e) {
			logger.error("Unable to load allow_sierra_fast_export from variables", e);
			return;
		}

		String apiVersion = cleanIniValue(ini.get("Catalog", "api_version"));
		if (apiVersion == null || apiVersion.length() == 0) {
			return;
		}
		apiBaseUrl = cleanIniValue(ini.get("Catalog", "url")) + "/iii/sierra-api/v" + apiVersion;

		//Last Update in UTC
		//Add a small buffer to be safe, this was 2 minutes.  Reducing to 15 seconds, should be 0
		Date lastExtractDate = new Date((lastSierraExtractTime - 15) * 1000);

		Date now       = new Date();
		Date yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);

		//TODO: this is a bad assumption now
		if (lastExtractDate.before(yesterday)) {
			logger.warn("Last Extract date was more than 24 hours ago.  Just getting the last 24 hours since we should have a full extract.");
			lastExtractDate = yesterday;
		}


		SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		dateTimeFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		String           lastExtractDateTimeFormatted = dateTimeFormatter.format(lastExtractDate);
		SimpleDateFormat dateFormatter                = new SimpleDateFormat("yyyy-MM-dd");
		dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		String lastExtractDateFormatted = dateFormatter.format(lastExtractDate);
		long   updateTime               = new Date().getTime() / 1000;
		logger.info("Loading records changed since " + lastExtractDateTimeFormatted);

		try {
			getWorkForPrimaryIdentifierStmt           = pikaConn.prepareStatement("SELECT id, grouped_work_id from grouped_work_primary_identifiers where type = ? and identifier = ?");
			deletePrimaryIdentifierStmt               = pikaConn.prepareStatement("DELETE from grouped_work_primary_identifiers where id = ? LIMIT 1");
			getAdditionalPrimaryIdentifierForWorkStmt = pikaConn.prepareStatement("SELECT * from grouped_work_primary_identifiers where grouped_work_id = ?");
			markGroupedWorkAsChangedStmt              = pikaConn.prepareStatement("UPDATE grouped_work SET date_updated = ? where id = ?");
			deleteGroupedWorkStmt                     = pikaConn.prepareStatement("DELETE from grouped_work where id = ?");
			getPermanentIdByWorkIdStmt                = pikaConn.prepareStatement("SELECT permanent_id from grouped_work WHERE id = ?");
		} catch (Exception e) {
			logger.error("Error setting up prepared statements for deleting bibs", e);
		}
		processDeletedBibs(ini, lastExtractDateFormatted, updateTime);
		getNewRecordsFromAPI(ini, lastExtractDateTimeFormatted, updateTime);
		getChangedRecordsFromAPI(ini, lastExtractDateTimeFormatted, updateTime);
		getNewItemsFromAPI(ini, lastExtractDateTimeFormatted);
		getChangedItemsFromAPI(ini, lastExtractDateTimeFormatted);
		getDeletedItemsFromAPI(ini, lastExtractDateFormatted);

	}

	private static int updateBibs(Ini ini) {
		//This section uses the batch method which doesn't work in Sierra because we are limited to 100 exports per hour

		addNoteToExportLog("Found " + allBibsToUpdate.size() + " bib records that need to be updated with data from Sierra.");
		int     batchSize           = 25;
		int     numProcessed        = 0;
		Long    exportStartTime     = new Date().getTime() / 1000;
		boolean hasMoreIdsToProcess = true;
		while (hasMoreIdsToProcess) {
			hasMoreIdsToProcess = false;
			StringBuilder     idsToProcess = new StringBuilder();
			int               maxIndex     = Math.min(allBibsToUpdate.size(), batchSize);
			ArrayList<String> ids          = new ArrayList<>();
			for (int i = 0; i < maxIndex; i++) {
				if (idsToProcess.length() > 0) {
					idsToProcess.append(",");
				}
				String lastId = allBibsToUpdate.last();
				idsToProcess.append(lastId);
				ids.add(lastId);
				allBibsToUpdate.remove(lastId);
			}
			updateMarcAndRegroupRecordIds(ini, idsToProcess.toString(), ids);
			if (allBibsToUpdate.size() >= 0) {
				numProcessed += maxIndex;
				if (numProcessed % 250 == 0 || allBibsToUpdate.size() == 0) {
					addNoteToExportLog("Processed " + numProcessed);
					if ((new Date().getTime() / 1000) - exportStartTime >= 5 * 60) {
						addNoteToExportLog("Stopping export due to time constraints, there are " + allBibsToUpdate.size() + " bibs remaining to be processed.");
						break;
					}
				}
				if (allBibsToUpdate.size() > 0) {
					hasMoreIdsToProcess = true;
				}
			}
		}

		return numProcessed;
	}

	private static void exportHolds(Connection sierraConn, Connection pikaConn) {
		Savepoint startOfHolds = null;
		try {
			logger.info("Starting export of holds");

			//Start a transaction so we can rebuild an entire table
			startOfHolds = pikaConn.setSavepoint();
			pikaConn.setAutoCommit(false);
			pikaConn.prepareCall("TRUNCATE TABLE ils_hold_summary").executeQuery();

			PreparedStatement addIlsHoldSummary = pikaConn.prepareStatement("INSERT INTO ils_hold_summary (ilsId, numHolds) VALUES (?, ?)");

			HashMap<String, Long> numHoldsByBib    = new HashMap<>();
			HashMap<String, Long> numHoldsByVolume = new HashMap<>();
			//Export bib level holds
			PreparedStatement bibHoldsStmt = sierraConn.prepareStatement("select count(hold.id) as numHolds, record_type_code, record_num from sierra_view.hold left join sierra_view.record_metadata on hold.record_id = record_metadata.id where record_type_code = 'b' and (status = '0' OR status = 't') GROUP BY record_type_code, record_num", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet         bibHoldsRS   = bibHoldsStmt.executeQuery();
			while (bibHoldsRS.next()) {
				String bibId = bibHoldsRS.getString("record_num");
				bibId = getfullSierraBibId(bibId);
				Long numHolds = bibHoldsRS.getLong("numHolds");
				numHoldsByBib.put(bibId, numHolds);
			}
			bibHoldsRS.close();

			if (exportItemHolds) {
				//Export item level holds
				PreparedStatement itemHoldsStmt = sierraConn.prepareStatement("select count(hold.id) as numHolds, record_num\n" +
						"from sierra_view.hold \n" +
						"inner join sierra_view.bib_record_item_record_link ON hold.record_id = item_record_id \n" +
						"inner join sierra_view.record_metadata on bib_record_item_record_link.bib_record_id = record_metadata.id \n" +
						"WHERE status = '0' OR status = 't' " +
						"group by record_num", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				ResultSet itemHoldsRS = itemHoldsStmt.executeQuery();
				while (itemHoldsRS.next()) {
					String bibId = itemHoldsRS.getString("record_num");
					bibId = getfullSierraBibId(bibId);
					Long numHolds = itemHoldsRS.getLong("numHolds");
					if (numHoldsByBib.containsKey(bibId)) {
						numHoldsByBib.put(bibId, numHolds + numHoldsByBib.get(bibId));
					} else {
						numHoldsByBib.put(bibId, numHolds);
					}
				}
				itemHoldsRS.close();
			}

			//Export volume level holds
			PreparedStatement volumeHoldsStmt = sierraConn.prepareStatement("select count(hold.id) as numHolds, bib_metadata.record_num as bib_num, volume_metadata.record_num as volume_num\n" +
					"from sierra_view.hold \n" +
					"inner join sierra_view.bib_record_volume_record_link ON hold.record_id = volume_record_id \n" +
					"inner join sierra_view.record_metadata as volume_metadata on bib_record_volume_record_link.volume_record_id = volume_metadata.id \n" +
					"inner join sierra_view.record_metadata as bib_metadata on bib_record_volume_record_link.bib_record_id = bib_metadata.id \n" +
					"WHERE status = '0' OR status = 't'\n" +
					"GROUP BY bib_metadata.record_num, volume_metadata.record_num", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			ResultSet volumeHoldsRS = volumeHoldsStmt.executeQuery();
			while (volumeHoldsRS.next()) {
				String bibId    = volumeHoldsRS.getString("bib_num");
				String volumeId = volumeHoldsRS.getString("volume_num");
				bibId    = getfullSierraBibId(bibId);
				volumeId = getfullSierraVolumeId(volumeId);
				Long numHolds = volumeHoldsRS.getLong("numHolds");
				//Do not count these in
				if (numHoldsByBib.containsKey(bibId)) {
					numHoldsByBib.put(bibId, numHolds + numHoldsByBib.get(bibId));
				} else {
					numHoldsByBib.put(bibId, numHolds);
				}
				if (numHoldsByVolume.containsKey(volumeId)) {
					numHoldsByVolume.put(volumeId, numHolds + numHoldsByVolume.get(bibId));
				} else {
					numHoldsByVolume.put(volumeId, numHolds);
				}
			}
			volumeHoldsRS.close();


			for (String bibId : numHoldsByBib.keySet()) {
				addIlsHoldSummary.setString(1, bibId);
				addIlsHoldSummary.setLong(2, numHoldsByBib.get(bibId));
				addIlsHoldSummary.executeUpdate();
			}

			for (String volumeId : numHoldsByVolume.keySet()) {
				addIlsHoldSummary.setString(1, volumeId);
				addIlsHoldSummary.setLong(2, numHoldsByVolume.get(volumeId));
				addIlsHoldSummary.executeUpdate();
			}

			try {
				pikaConn.commit();
				pikaConn.setAutoCommit(true);
			} catch (Exception e) {
				logger.warn("error committing hold updates rolling back", e);
				pikaConn.rollback(startOfHolds);
			}

		} catch (Exception e) {
			logger.error("Unable to export holds from Sierra", e);
			if (startOfHolds != null) {
				try {
					pikaConn.rollback(startOfHolds);
				} catch (Exception e1) {
					logger.error("Unable to rollback due to exception", e1);
				}
			}
		}
		logger.info("Finished exporting holds");
	}

	private static String getfullSierraBibId(String bibId) {
		return ".b" + bibId + getCheckDigit(bibId);
	}

	private static String getfullSierraItemId(String itemId) {
		return ".i" + itemId + getCheckDigit(itemId);
	}

	private static String getfullSierraVolumeId(String volumeId) {
		return ".j" + volumeId + getCheckDigit(volumeId);
	}


	private static PreparedStatement getWorkForPrimaryIdentifierStmt;
	private static PreparedStatement getAdditionalPrimaryIdentifierForWorkStmt;
	private static PreparedStatement deletePrimaryIdentifierStmt;
	private static PreparedStatement markGroupedWorkAsChangedStmt;
	private static PreparedStatement deleteGroupedWorkStmt;
	private static PreparedStatement getPermanentIdByWorkIdStmt;

	private static void processDeletedBibs(Ini ini, String lastExtractDateFormatted, long updateTime) {
		//Get a list of deleted bibs
		addNoteToExportLog("Starting to process deleted records since " + lastExtractDateFormatted);

		boolean hasMoreRecords;
		int     bufferSize   = 250;
		long    offset       = 0;
		int     numDeletions = 0;

		do {
			hasMoreRecords = false;
			String url = apiBaseUrl + "/bibs/?deletedDate=[" + lastExtractDateFormatted + ",]&fields=id&deleted=true&limit=" + bufferSize;
			if (offset > 0) {
				url += "&offset=" + offset;
			}
			JSONObject deletedRecords = callSierraApiURL(ini, apiBaseUrl, url, debug);

			if (deletedRecords != null) {
				try {
					JSONArray entries = deletedRecords.getJSONArray("entries");
					for (int i = 0; i < entries.length(); i++) {
						JSONObject curBib = entries.getJSONObject(i);
						String     id     = curBib.getString("id");
						allDeletedIds.add(id);
					}
					//If nothing has been deleted, iii provides entries, but not a total
					if (deletedRecords.has("total") && deletedRecords.getLong("total") >= bufferSize) {
						offset += deletedRecords.getLong("total");
						hasMoreRecords = true;
					}
				} catch (Exception e) {
					logger.error("Error processing deleted bibs", e);
				}
			}
		} while (hasMoreRecords);


		if (allDeletedIds.size() > 0) {
			for (String id : allDeletedIds) {
				if (deleteRecord(updateTime, id)) {
					numDeletions++;
				}
			}
			addNoteToExportLog("Finished processing deleted records, of " + allDeletedIds.size() + " records reported by the API, " + numDeletions + " were deleted.");
		} else {
			addNoteToExportLog("No deleted records found");
		}
	}

	private static boolean deleteRecord(long updateTime, String idFromAPI) {
		String bibId = ".b" + idFromAPI + getCheckDigit(idFromAPI);
		try {
			//Check to see if the identifier is in the grouped work primary identifiers table
			getWorkForPrimaryIdentifierStmt.setString(1, indexingProfile.name);
			getWorkForPrimaryIdentifierStmt.setString(2, bibId);
			try (ResultSet getWorkForPrimaryIdentifierRS = getWorkForPrimaryIdentifierStmt.executeQuery()) {
				if (getWorkForPrimaryIdentifierRS.next()) { // If not true, already deleted skip this
					Long groupedWorkId       = getWorkForPrimaryIdentifierRS.getLong("grouped_work_id");
					Long primaryIdentifierId = getWorkForPrimaryIdentifierRS.getLong("id");

					//Delete the primary identifier
					deleteGroupedWorkPrimaryIdentifier(primaryIdentifierId);

					//Check to see if there are other identifiers for this work
					getAdditionalPrimaryIdentifierForWorkStmt.setLong(1, groupedWorkId);
					try (ResultSet getAdditionalPrimaryIdentifierForWorkRS = getAdditionalPrimaryIdentifierForWorkStmt.executeQuery()) {
						if (getAdditionalPrimaryIdentifierForWorkRS.next()) {
							//There are additional records for this work, just need to mark that it needs indexing again
							// So that the work is still in the index, but with out this particular bib
							markGroupedWorkForReindexing(updateTime, groupedWorkId);
							return true;
						} else {
							//The grouped work no longer exists
							String permanentId = getPermanentIdForGroupedWork(groupedWorkId);
							if (permanentId != null && !permanentId.isEmpty()) {
								//Delete the work from solr index
								groupedWorkIndexer.deleteRecord(permanentId);
//								logger.warn("Sierra API extract deleted Group Work " + permanentId + " from index. Investigate if it is an anomalous deletion by the Sierra API extract");
								//pascal 5/2/2019 cutting out warning noise for now

								// See https://marmot.myjetbrains.com/youtrack/issue/D-2364

								//Prevent deletion of the grouped work entry for now. Pascal 8-3-2018
								//Delete the work from the database?
								//TODO: Should we do this or leave a record if it was linked to lists, reading history, etc?
								//regular indexer deletes them too
//								deleteGroupedWorkStmt.setLong(1, groupedWorkId);
//								deleteGroupedWorkStmt.executeUpdate();

								return true;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error processing deleted bibs", e);
		}
		return false;
	}

	private static void deleteGroupedWorkPrimaryIdentifier(Long primaryIdentifierId) {
		try {
			deletePrimaryIdentifierStmt.setLong(1, primaryIdentifierId);
			deletePrimaryIdentifierStmt.executeUpdate();
		} catch (SQLException e) {
			logger.error("Error deleting grouped work primary identifier from database", e);
		}
	}

	private static String getPermanentIdForGroupedWork(Long groupedWorkId) {
		String permanentId = null;
		try {
			getPermanentIdByWorkIdStmt.setLong(1, groupedWorkId);
			try (ResultSet getPermanentIdByWorkIdRS = getPermanentIdByWorkIdStmt.executeQuery()) {
				if (getPermanentIdByWorkIdRS.next()) {
					permanentId = getPermanentIdByWorkIdRS.getString("permanent_id");
				}
			}
		} catch (SQLException e) {
			logger.error("Error looking up grouped work permanent Id", e);
		}
		return permanentId;
	}

	private static void markGroupedWorkForReindexing(long updateTime, Long groupedWorkId) {
		try {
			markGroupedWorkAsChangedStmt.setLong(1, updateTime);
			markGroupedWorkAsChangedStmt.setLong(2, groupedWorkId);
			markGroupedWorkAsChangedStmt.executeUpdate();
		} catch (SQLException e) {
			logger.error("Error while marking a grouped work for reindexing", e);
		}
	}

	private static void getChangedRecordsFromAPI(Ini ini, String lastExtractDateFormatted, long updateTime) {
		//Get a list of deleted bibs
		addNoteToExportLog("Starting to process records changed since " + lastExtractDateFormatted);
		boolean hasMoreRecords;
		int     bufferSize           = 1000;
		int     numChangedRecords    = 0;
		int     numSuppressedRecords = 0;
		int     recordOffset         = 50000;
		long    firstRecordIdToLoad  = 1;
		do {
			hasMoreRecords = false;
			String url = apiBaseUrl + "/bibs/?updatedDate=[" + lastExtractDateFormatted + ",]&deleted=false&fields=id,suppressed&limit=" + bufferSize;
			if (firstRecordIdToLoad > 1) {
				url += "&id=[" + firstRecordIdToLoad + ",]";
			}
			JSONObject createdRecords = callSierraApiURL(ini, apiBaseUrl, url, debug);
			if (createdRecords != null) {
				try {
					JSONArray entries = createdRecords.getJSONArray("entries");
					int       lastId  = 0;
					for (int i = 0; i < entries.length(); i++) {
						JSONObject curBib = entries.getJSONObject(i);
						lastId = curBib.getInt("id");
						boolean isSuppressed = false;
						if (curBib.has("suppressed")) {
							isSuppressed = curBib.getBoolean("suppressed");
						}
						if (isSuppressed) {
							String id = curBib.getString("id");
							allDeletedIds.add(id);
							if (deleteRecord(updateTime, id)) {
								numSuppressedRecords++;
							}

						} else {
							allBibsToUpdate.add(curBib.getString("id"));
							numChangedRecords++;
						}
					}
					if (createdRecords.getLong("total") >= bufferSize) {
						hasMoreRecords = true;
					}
					if (entries.length() >= bufferSize) {
						firstRecordIdToLoad = lastId + 1;
					} else {
						firstRecordIdToLoad += recordOffset;
					}
					//Get the grouped work id for the new bib
				} catch (Exception e) {
					logger.error("Error processing changed bibs", e);
				}
			} else {
				addNoteToExportLog("No changed records found");
			}
		} while (hasMoreRecords);
		addNoteToExportLog("Finished processing changed records, there were " + numChangedRecords + " changed records and " + numSuppressedRecords + " suppressed records");
	}

	private static void getNewRecordsFromAPI(Ini ini, String lastExtractDateFormatted, long updateTime) {
		//Get a list of deleted bibs
		addNoteToExportLog("Starting to process records created since " + lastExtractDateFormatted);
		boolean hasMoreRecords;
		int     bufferSize           = 1000;
		long    offset               = 0;
		int     numNewRecords        = 0;
		int     numSuppressedRecords = 0;

		do {
			hasMoreRecords = false;
			String url = apiBaseUrl + "/bibs/?createdDate=[" + lastExtractDateFormatted + ",]&deleted=false&fields=id,suppressed&limit=" + bufferSize;
			if (offset > 0) {
				url += "&offset=" + offset;
			}
			JSONObject createdRecords = callSierraApiURL(ini, apiBaseUrl, url, debug);
			if (createdRecords != null) {
				try {
					JSONArray entries = createdRecords.getJSONArray("entries");
					for (int i = 0; i < entries.length(); i++) {
						JSONObject curBib       = entries.getJSONObject(i);
						boolean    isSuppressed = false;
						if (curBib.has("suppressed")) {
							isSuppressed = curBib.getBoolean("suppressed");
						}
						if (isSuppressed) {
							String id = curBib.getString("id");
							allDeletedIds.add(id);
							if (deleteRecord(updateTime, id)) {
								numSuppressedRecords++;
							}
						} else {
							allBibsToUpdate.add(curBib.getString("id"));
							numNewRecords++;
						}
					}
					if (createdRecords.getLong("total") >= bufferSize) {
						offset += createdRecords.getLong("total");
						hasMoreRecords = true;
					}
					//Get the grouped work id for the new bib
				} catch (Exception e) {
					logger.error("Error processing newly created bibs", e);
				}
			} else {
				addNoteToExportLog("No newly created records found");
			}
		} while (hasMoreRecords);
		addNoteToExportLog("Finished processing newly created records " + numNewRecords + " were new and " + numSuppressedRecords + " were suppressed");
	}

	private static void getNewItemsFromAPI(Ini ini, String lastExtractDateFormatted) {
		//Get a list of deleted bibs
		addNoteToExportLog("Starting to process items created since " + lastExtractDateFormatted);
		boolean hasMoreRecords;
		int     bufferSize    = 1000;
		long    offset        = 0;
		int     numNewRecords = 0;
		do {
			hasMoreRecords = false;
			String url = apiBaseUrl + "/items/?createdDate=[" + lastExtractDateFormatted + ",]&deleted=false&fields=id,bibIds&limit=" + bufferSize;
			if (offset > 0) {
				url += "&offset=" + offset;
			}
			JSONObject createdRecords = callSierraApiURL(ini, apiBaseUrl, url, debug);
			if (createdRecords != null) {
				try {
					JSONArray entries = createdRecords.getJSONArray("entries");
					for (int i = 0; i < entries.length(); i++) {
						JSONObject curBib = entries.getJSONObject(i);
						JSONArray  bibIds = curBib.getJSONArray("bibIds");
						for (int j = 0; j < bibIds.length(); j++) {
							String id = bibIds.getString(j);
							if (!allDeletedIds.contains(id) && !allBibsToUpdate.contains(id)) {
								allBibsToUpdate.add(id);
							}
							numNewRecords++;
						}
					}
					if (createdRecords.getLong("total") >= bufferSize) {
						offset += createdRecords.getLong("total");
						hasMoreRecords = true;
					}
					//Get the grouped work id for the new bib
				} catch (Exception e) {
					logger.error("Error processing newly created items", e);
				}
			} else {
				addNoteToExportLog("No newly created items found");
			}
		} while (hasMoreRecords);
		addNoteToExportLog("Finished processing newly created items " + numNewRecords);
	}

	private static void getChangedItemsFromAPI(Ini ini, String lastExtractDateFormatted) {
		//Get a list of deleted bibs
		addNoteToExportLog("Starting to process items updated since " + lastExtractDateFormatted);
		boolean hasMoreRecords;
		int     bufferSize          = 1000;
		int     numChangedItems     = 0;
		int     numNewBibs          = 0;
		long    firstRecordIdToLoad = 1;
		int     recordOffset        = 50000;
		do {
			hasMoreRecords = false;
			String url = apiBaseUrl + "/items/?updatedDate=[" + lastExtractDateFormatted + ",]&deleted=false&fields=id,bibIds&limit=" + bufferSize;
			if (firstRecordIdToLoad > 1) {
				url += "&id=[" + firstRecordIdToLoad + ",]";
			}
			JSONObject createdRecords = callSierraApiURL(ini, apiBaseUrl, url, debug);
			if (createdRecords != null) {
				try {
					JSONArray entries = createdRecords.getJSONArray("entries");
					int       lastId  = 0;
					for (int i = 0; i < entries.length(); i++) {
						JSONObject curItem = entries.getJSONObject(i);
						lastId = curItem.getInt("id");
						if (curItem.has("bibIds")) {
							JSONArray bibIds = curItem.getJSONArray("bibIds");
							for (int j = 0; j < bibIds.length(); j++) {
								String id = bibIds.getString(j);
								if (!allDeletedIds.contains(id) && !allBibsToUpdate.contains(id)) {
									allBibsToUpdate.add(id);
									numNewBibs++;
								}
								numChangedItems++;
							}
						}
					}
					if (createdRecords.getLong("total") >= bufferSize) {
						hasMoreRecords = true;
					}
					if (entries.length() >= bufferSize) {
						firstRecordIdToLoad = lastId + 1;
					} else {
						firstRecordIdToLoad += recordOffset;
					}
					//Get the grouped work id for the new bib
				} catch (Exception e) {
					logger.error("Error processing updated items", e);
				}
			} else {
				addNoteToExportLog("No updated items found");
			}
		} while (hasMoreRecords);
		addNoteToExportLog("Finished processing updated items " + numChangedItems + " this added " + numNewBibs + " bibs to process");
	}

	private static void getDeletedItemsFromAPI(Ini ini, String lastExtractDateFormatted) {
		//Get a list of deleted bibs
		addNoteToExportLog("Starting to process items deleted since " + lastExtractDateFormatted);
		boolean hasMoreRecords;
		int     bufferSize      = 1000;
		long    offset          = 0;
		int     numDeletedItems = 0;
		do {
			hasMoreRecords = false;
			String url = apiBaseUrl + "/items/?deletedDate=[" + lastExtractDateFormatted + ",]&deleted=true&fields=id,bibIds&limit=" + bufferSize;
			//TODO: It appears bibIds aren't being returned
			if (offset > 0) {
				url += "&offset=" + offset;
			}
			JSONObject createdRecords = callSierraApiURL(ini, apiBaseUrl, url, debug);
			if (createdRecords != null) {
				try {
					JSONArray entries = createdRecords.getJSONArray("entries");
					for (int i = 0; i < entries.length(); i++) {
						JSONObject curBib = entries.getJSONObject(i);
						JSONArray  bibIds = curBib.getJSONArray("bibIds");
						for (int j = 0; j < bibIds.length(); j++) {
							String id = bibIds.getString(j);
							if (!allDeletedIds.contains(id) && !allBibsToUpdate.contains(id)) {
								allBibsToUpdate.add(id);
							}
						}
					}
					if (createdRecords.getLong("total") >= bufferSize) {
						offset += createdRecords.getLong("total");
						hasMoreRecords = true;
					}
					//Get the grouped work id for the new bib
				} catch (Exception e) {
					logger.error("Error processing deleted items", e);
				}
			} else {
				addNoteToExportLog("No deleted items found");
			}
		} while (hasMoreRecords);
		addNoteToExportLog("Finished processing deleted items found " + numDeletedItems);
	}

	private static MarcFactory marcFactory = MarcFactory.newInstance();

	private static boolean updateMarcAndRegroupRecordId(Ini ini, String id) {
		try {
			JSONObject marcResults = getMarcJSONFromSierraApiURL(ini, apiBaseUrl, apiBaseUrl + "/bibs/" + id + "/marc");
			if (marcResults != null) {
				if (marcResults.has("httpStatus")) {
					if (marcResults.getInt("code") == 107) {
						//This record was deleted
						logger.debug("id " + id + " was deleted");
						return true;
					} else {
						logger.error("Unknown error " + marcResults);
						return false;
					}
				}
				String    leader     = marcResults.has("leader") ? marcResults.getString("leader") : "";
				Record    marcRecord = marcFactory.newRecord(leader);
				JSONArray fields     = marcResults.getJSONArray("fields");
				for (int i = 0; i < fields.length(); i++) {
					JSONObject                                      fieldData = fields.getJSONObject(i);
					@SuppressWarnings("unchecked") Iterator<String> tags      = (Iterator<String>) fieldData.keys();
					while (tags.hasNext()) {
						String tag = tags.next();
						if (fieldData.get(tag) instanceof JSONObject) {
							JSONObject fieldDataDetails = fieldData.getJSONObject(tag);
							char       ind1             = fieldDataDetails.getString("ind1").charAt(0);
							char       ind2             = fieldDataDetails.getString("ind2").charAt(0);
							DataField  dataField        = marcFactory.newDataField(tag, ind1, ind2);
							JSONArray  subfields        = fieldDataDetails.getJSONArray("subfields");
							for (int j = 0; j < subfields.length(); j++) {
								JSONObject subfieldData         = subfields.getJSONObject(j);
								String     subfieldIndicatorStr = (String) subfieldData.keys().next();
								char       subfieldIndicator    = subfieldIndicatorStr.charAt(0);
								String     subfieldValue        = subfieldData.getString(subfieldIndicatorStr);
								dataField.addSubfield(marcFactory.newSubfield(subfieldIndicator, subfieldValue));
							}
							marcRecord.addVariableField(dataField);
						} else {
							String fieldValue = fieldData.getString(tag);
							marcRecord.addVariableField(marcFactory.newControlField(tag, fieldValue));
						}
					}
				}
				logger.debug("Converted JSON to MARC for Bib");

				//Add the identifier
				marcRecord.addVariableField(marcFactory.newDataField(indexingProfile.recordNumberTag, ' ', ' ', "" + indexingProfile.recordNumberField /*convert to string*/, getfullSierraBibId(id)));

				//Load Sierra Fixed Field / Bib Level Tag
				JSONObject fixedFieldResults = getMarcJSONFromSierraApiURL(ini, apiBaseUrl, apiBaseUrl + "/bibs/" + id + "?fields=fixedFields,locations");
				if (fixedFieldResults != null) {
					String    bCode3           = fixedFieldResults.getJSONObject("fixedFields").getJSONObject("31").getString("value");
					String    matType          = fixedFieldResults.getJSONObject("fixedFields").getJSONObject("30").getString("value");
					String    location         = fixedFieldResults.getJSONObject("fixedFields").getJSONObject("26").getString("value");
					DataField sierraFixedField = marcFactory.newDataField(indexingProfile.bcode3DestinationField, ' ', ' ');
					sierraFixedField.addSubfield(marcFactory.newSubfield(indexingProfile.bcode3DestinationSubfield, bCode3));
					sierraFixedField.addSubfield(marcFactory.newSubfield(indexingProfile.materialTypeSubField, matType));
					if (location.equalsIgnoreCase("multi")) {
						JSONArray locationsJSON = fixedFieldResults.getJSONArray("locations");
						for (int k = 0; k < locationsJSON.length(); k++) {
							location = locationsJSON.getJSONObject(k).getString("code");
							sierraFixedField.addSubfield(marcFactory.newSubfield(bibLevelLocationsSubfield, location));
						}
					} else {
						sierraFixedField.addSubfield(marcFactory.newSubfield(bibLevelLocationsSubfield, location));

					}

					marcRecord.addVariableField(sierraFixedField);
				}

				//Get Items for the bib record
				getItemsForBib(ini, id, marcRecord);
				logger.debug("Processed items for Bib");
				RecordIdentifier recordIdentifier = recordGroupingProcessor.getPrimaryIdentifierFromMarcRecord(marcRecord, indexingProfile.name, indexingProfile.doAutomaticEcontentSuppression);
				String           identifier       = recordIdentifier.getIdentifier();
				writeMarcRecord(marcRecord, identifier);
				logger.debug("Wrote marc record for " + identifier);

				//Setup the grouped work for the record.  This will take care of either adding it to the proper grouped work
				//or creating a new grouped work
				if (!recordGroupingProcessor.processMarcRecord(marcRecord, true)) {
					logger.warn(identifier + " was suppressed");
				} else {
					logger.debug("Finished record grouping for " + identifier);
				}
			} else {
				logger.error("Error exporting marc record for " + id + " call returned null");
				return false;
			}
		} catch (Exception e) {
			logger.error("Error in updateMarcAndRegroupRecordId processing bib from Sierra API", e);
			return false;
		}
		return true;
	}


	private static SimpleDateFormat sierraAPIDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private static void getItemsForBib(Ini ini, String id, Record marcRecord) {
		//Get a list of all items
		long startTime = new Date().getTime();
		//This will return a 404 error if all items are suppressed or if the record has not items
		JSONObject itemIds = callSierraApiURL(ini, apiBaseUrl, apiBaseUrl + "/items?limit=1000&deleted=false&suppressed=false&fields=id,updatedDate,createdDate,location,status,barcode,callNumber,itemType,fixedFields,varFields&bibIds=" + id, debug);
		if (itemIds != null) {
			try {
				if (itemIds.has("code")) {
					if (itemIds.getInt("code") != 404) {
						logger.error("Error getting information about items " + itemIds.toString());
					}
				} else {
					JSONArray entries = itemIds.getJSONArray("entries");
					logger.debug("finished getting items for " + id + " elapsed time " + (new Date().getTime() - startTime) + "ms found " + entries.length());
					for (int i = 0; i < entries.length(); i++) {
						JSONObject curItem     = entries.getJSONObject(i);
						JSONObject fixedFields = curItem.getJSONObject("fixedFields");
						JSONArray  varFields   = curItem.getJSONArray("varFields");
						String     itemId      = curItem.getString("id");
						DataField  itemField   = marcFactory.newDataField(indexingProfile.itemTag, ' ', ' ');
						//Record Number
						if (indexingProfile.itemRecordNumberSubfield != ' ') {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.itemRecordNumberSubfield, getfullSierraItemId(itemId)));
						}
						//barcode
						if (curItem.has("barcode")) {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.barcodeSubfield, curItem.getString("barcode")));
						}
						//location
						if (curItem.has("location") && indexingProfile.locationSubfield != ' ') {
							String locationCode = curItem.getJSONObject("location").getString("code");
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.locationSubfield, locationCode));
						}
						//call number (can we get prestamp cutter, poststamp?
					/*if (curItem.has("callNumber") && indexingProfile.callNumberSubfield != ' '){
						itemField.addSubfield(marcFactory.newSubfield(indexingProfile.callNumberSubfield, curItem.getString("callNumber")));
					}*/
						//status
						if (curItem.has("status")) {
							String statusCode = curItem.getJSONObject("status").getString("code");
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.itemStatusSubfield, statusCode));
							if (curItem.getJSONObject("status").has("duedate")) {
								Date createdDate = sierraAPIDateFormatter.parse(curItem.getJSONObject("status").getString("duedate"));
								itemField.addSubfield(marcFactory.newSubfield(indexingProfile.dueDateSubfield, indexingProfile.dueDateFormatter.format(createdDate)));
							} else {
								itemField.addSubfield(marcFactory.newSubfield(indexingProfile.dueDateSubfield, ""));
							}
						} else {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.dueDateSubfield, ""));
						}
						//total checkouts
						if (fixedFields.has("76") && indexingProfile.totalCheckoutsSubfield != ' ') {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.totalCheckoutsSubfield, fixedFields.getJSONObject("76").getString("value")));
						}
						//last year checkouts
						if (fixedFields.has("110") && indexingProfile.lastYearCheckoutsSubfield != ' ') {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.lastYearCheckoutsSubfield, fixedFields.getJSONObject("110").getString("value")));
						}
						//year to date checkouts
						if (fixedFields.has("109") && indexingProfile.yearToDateCheckoutsSubfield != ' ') {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.yearToDateCheckoutsSubfield, fixedFields.getJSONObject("109").getString("value")));
						}
						//total renewals
						if (fixedFields.has("77") && indexingProfile.totalRenewalsSubfield != ' ') {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.totalRenewalsSubfield, fixedFields.getJSONObject("77").getString("value")));
						}
						//iType
						if (fixedFields.has("61") && indexingProfile.iTypeSubfield != ' ') {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.iTypeSubfield, fixedFields.getJSONObject("61").getString("value")));
						}
						//date created
						if (curItem.has("createdDate") && indexingProfile.dateCreatedSubfield != ' ') {
							Date createdDate = sierraAPIDateFormatter.parse(curItem.getString("createdDate"));
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.dateCreatedSubfield, indexingProfile.dateCreatedFormatter.format(createdDate)));
						}
						//last check in date
						if (fixedFields.has("68") && indexingProfile.lastCheckinDateSubfield != ' ') {
							Date lastCheckin = sierraAPIDateFormatter.parse(fixedFields.getString("68"));
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.lastCheckinDateSubfield, indexingProfile.lastCheckinFormatter.format(lastCheckin)));
						}
						//icode2
						if (fixedFields.has("60") && indexingProfile.iCode2Subfield != ' ') {
							itemField.addSubfield(marcFactory.newSubfield(indexingProfile.iCode2Subfield, fixedFields.getJSONObject("60").getString("value")));
						}

						//Process variable fields
						for (int j = 0; j < varFields.length(); j++) {
							JSONObject    curVarField     = varFields.getJSONObject(j);
							String        fieldTag        = curVarField.getString("fieldTag");
							StringBuilder allFieldContent = new StringBuilder();
							JSONArray     subfields       = null;
							if (curVarField.has("subfields")) {
								subfields = curVarField.getJSONArray("subfields");
								for (int k = 0; k < subfields.length(); k++) {
									JSONObject subfield = subfields.getJSONObject(k);
									allFieldContent.append(subfield.getString("content"));
								}
							} else {
								allFieldContent.append(curVarField.getString("content"));
							}

							if (fieldTag.equals(indexingProfile.callNumberExportFieldTag)) {
								if (subfields != null) {
									for (int k = 0; k < subfields.length(); k++) {
										JSONObject subfield = subfields.getJSONObject(k);
										String     tag      = subfield.getString("tag");
										String     content  = subfield.getString("content");
										if (indexingProfile.callNumberPrestampExportSubfield.length() > 0 && tag.equalsIgnoreCase(indexingProfile.callNumberPrestampExportSubfield)) {
											itemField.addSubfield(marcFactory.newSubfield(indexingProfile.callNumberPrestampSubfield, content));
										} else if (indexingProfile.callNumberExportSubfield.length() > 0 && tag.equalsIgnoreCase(indexingProfile.callNumberExportSubfield)) {
											itemField.addSubfield(marcFactory.newSubfield(indexingProfile.callNumberSubfield, content));
										} else if (indexingProfile.callNumberCutterExportSubfield.length() > 0 && tag.equalsIgnoreCase(indexingProfile.callNumberCutterExportSubfield)) {
											itemField.addSubfield(marcFactory.newSubfield(indexingProfile.callNumberCutterSubfield, content));
										} else if (indexingProfile.callNumberPoststampExportSubfield.length() > 0 && tag.indexOf(indexingProfile.callNumberPoststampExportSubfield) > 0) {
											itemField.addSubfield(marcFactory.newSubfield(indexingProfile.callNumberPoststampSubfield, content));
											//}else{
											//logger.debug("Unhandled call number subfield " + tag);
										}
									}
								} else {
									String content = curVarField.getString("content");
									itemField.addSubfield(marcFactory.newSubfield(indexingProfile.callNumberSubfield, content));
								}
							} else if (indexingProfile.volumeExportFieldTag.length() > 0 && fieldTag.equals(indexingProfile.volumeExportFieldTag)) {
								itemField.addSubfield(marcFactory.newSubfield(indexingProfile.volume, allFieldContent.toString()));
							} else if (indexingProfile.urlExportFieldTag.length() > 0 && fieldTag.equals(indexingProfile.urlExportFieldTag)) {
								itemField.addSubfield(marcFactory.newSubfield(indexingProfile.itemUrl, allFieldContent.toString()));
							} else if (indexingProfile.eContentExportFieldTag.length() > 0 && fieldTag.equals(indexingProfile.eContentExportFieldTag)) {
								itemField.addSubfield(marcFactory.newSubfield(indexingProfile.eContentDescriptor, allFieldContent.toString()));
								//}else{
								//logger.debug("Unhandled item variable field " + fieldTag);
							}
						}
						marcRecord.addVariableField(itemField);
					}
				}
			} catch (Exception e) {
				logger.error("Error getting information about items", e);
			}
		} else {
			logger.debug("finished getting items for " + id + " elapsed time " + (new Date().getTime() - startTime) + "ms found none");
		}
	}

	private static void updateMarcAndRegroupRecordIds(Ini ini, String ids, ArrayList<String> idArray) {
		try {
			JSONObject marcResults = null;
			if (allowFastExportMethod) {
				//Don't log errors since we get regular errors if we exceed the export rate.
				logger.debug("Loading marc records with fast method " + apiBaseUrl + "/bibs/marc?id=" + ids);
				marcResults = callSierraApiURL(ini, apiBaseUrl, apiBaseUrl + "/bibs/marc?id=" + ids, debug);
			}
			if (marcResults != null && marcResults.has("file")) {
				logger.debug("Got results with fast method");
				ArrayList<String> processedIds = new ArrayList<>();
				String            dataFileUrl  = marcResults.getString("file");
				String            marcData     = getMarcFromSierraApiURL(ini, apiBaseUrl, dataFileUrl, debug);
				logger.debug("Got marc record file");
				MarcReader marcReader = new MarcPermissiveStreamReader(new ByteArrayInputStream(marcData.getBytes(StandardCharsets.UTF_8)), true, true);
				while (marcReader.hasNext()) {
					try {
						logger.debug("Starting to process the next marc Record");

						Record marcRecord = marcReader.next();
						logger.debug("Got the next marc Record data");

						RecordIdentifier recordIdentifier = recordGroupingProcessor.getPrimaryIdentifierFromMarcRecord(marcRecord, indexingProfile.name, indexingProfile.doAutomaticEcontentSuppression);
						String           identifier       = recordIdentifier.getIdentifier();
						logger.debug("Writing marc record for " + identifier);

						writeMarcRecord(marcRecord, identifier);
						logger.debug("Wrote marc record for " + identifier);

						//Setup the grouped work for the record.  This will take care of either adding it to the proper grouped work
						//or creating a new grouped work
						if (!recordGroupingProcessor.processMarcRecord(marcRecord, true)) {
							logger.warn(identifier + " was suppressed");
						} else {
							logger.debug("Finished record grouping for " + identifier);
						}
						String shortId = identifier.substring(2, identifier.length() - 1);
						processedIds.add(shortId);
						logger.debug("Processed " + identifier);
					} catch (MarcException mre) {
						logger.info("Error loading marc record from file, will load manually");
					}
				}
				for (String id : idArray) {
					if (!processedIds.contains(id)) {
						if (!updateMarcAndRegroupRecordId(ini, id)) {
							//Don't fail the entire process.  We will just reprocess next time the export runs
							logger.debug("Processing " + id + " failed");
							addNoteToExportLog("Processing " + id + " failed");
							bibsWithErrors.add(id);
							//allPass = false;
						} else {
							logger.debug("Processed " + id);
						}
					}
				}
			} else {
				logger.debug("No results with fast method available, loading with slow method");
				//Don't need this message since it will happen regularly.
				//logger.info("Error exporting marc records for " + ids + " marc results did not have a file");
				for (String id : idArray) {
					logger.debug("starting to process " + id);
					if (!updateMarcAndRegroupRecordId(ini, id)) {
						//Don't fail the entire process.  We will just reprocess next time the export runs
						addNoteToExportLog("Processing " + id + " failed");
						bibsWithErrors.add(id);
						//allPass = false;
					}
				}
				logger.debug("finished processing " + idArray.size() + " records with the slow method");
			}
		} catch (Exception e) {
			logger.error("Error processing newly created bibs", e);
		}
	}

	private static void writeMarcRecord(Record marcRecord, String identifier) {
		File marcFile = indexingProfile.getFileForIlsRecord(identifier);
		if (!marcFile.getParentFile().exists()) {
			if (!marcFile.getParentFile().mkdirs()) {
				logger.error("Could not create directories for " + marcFile.getAbsolutePath());
			}
		}
		try {
			MarcWriter marcWriter = new MarcStreamWriter(new FileOutputStream(marcFile), true);
			marcWriter.write(marcRecord);
			marcWriter.close();
		} catch (FileNotFoundException e) {
			logger.warn("File not found exception ", e);
		}
	}

	private static void exportDueDates(String exportPath, Connection conn) throws SQLException, IOException {
		addNoteToExportLog("Starting export of due dates");
		String            dueDatesSQL     = "select record_num, due_gmt from sierra_view.checkout inner join sierra_view.item_view on item_record_id = item_view.id where due_gmt is not null";
		PreparedStatement getDueDatesStmt = conn.prepareStatement(dueDatesSQL, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		ResultSet         dueDatesRS      = null;
		boolean           loadError       = false;
		try {
			dueDatesRS = getDueDatesStmt.executeQuery();
		} catch (SQLException e1) {
			logger.error("Error loading active orders", e1);
			loadError = true;
		}
		if (!loadError) {
			File      dueDateFile   = new File(exportPath + "/due_dates.csv");
			CSVWriter dueDateWriter = new CSVWriter(new FileWriter(dueDateFile));
			while (dueDatesRS.next()) {
				try {
					String recordNum = dueDatesRS.getString("record_num");
					if (recordNum != null) {
						String dueDateRaw = dueDatesRS.getString("due_gmt");
						String itemId     = getfullSierraItemId(recordNum);
						Date   dueDate    = dueDatesRS.getDate("due_gmt");
						dueDateWriter.writeNext(new String[]{itemId, Long.toString(dueDate.getTime()), dueDateRaw});
					} else {
						logger.warn("No record number found while exporting due dates");
					}
				} catch (Exception e) {
					logger.error("Error writing due dates", e);
				}
			}
			dueDateWriter.close();
			dueDatesRS.close();
		}
		addNoteToExportLog("Finished exporting due dates");
	}

	private static void exportActiveOrders(String exportPath, Connection conn) throws SQLException, IOException {
		addNoteToExportLog("Starting export of active orders");

		//Load the orders we had last time
		File                     orderRecordFile        = new File(exportPath + "/active_orders.csv");
		HashMap<String, Integer> existingBibsWithOrders = new HashMap<>();
		readOrdersFile(orderRecordFile, existingBibsWithOrders);

		boolean suppressOrderRecordsThatAreReceivedAndCatalogged = convertConfigStringToBoolean(cleanIniValue(ini.get("Catalog", "suppressOrderRecordsThatAreReceivedAndCatalogged")));
		boolean suppressOrderRecordsThatAreCatalogged            = convertConfigStringToBoolean(cleanIniValue(ini.get("Catalog", "suppressOrderRecordsThatAreCatalogged")));
		boolean suppressOrderRecordsThatAreReceived              = convertConfigStringToBoolean(cleanIniValue(ini.get("Catalog", "suppressOrderRecordsThatAreReceived")));

		String orderStatusesToExport = cleanIniValue(ini.get("Reindex", "orderStatusesToExport"));
		if (orderStatusesToExport == null) {
			orderStatusesToExport = "o|1";
		}
		String[]      orderStatusesToExportVals = orderStatusesToExport.split("\\|");
		StringBuilder orderStatusCodesSQL       = new StringBuilder();
		for (String orderStatusesToExportVal : orderStatusesToExportVals) {
			if (orderStatusCodesSQL.length() > 0) {
				orderStatusCodesSQL.append(" OR ");
			}
			orderStatusCodesSQL.append(" order_status_code = '").append(orderStatusesToExportVal).append("'");
		}
		String activeOrderSQL = "SELECT bib_view.record_num AS bib_record_num, order_view.record_num AS order_record_num, accounting_unit_code_num, order_status_code, copies, location_code, catalog_date_gmt, received_date_gmt " +
				"FROM sierra_view.order_view " +
				"INNER JOIN sierra_view.bib_record_order_record_link ON bib_record_order_record_link.order_record_id = order_view.record_id " +
				"INNER JOIN sierra_view.bib_view ON sierra_view.bib_view.id = bib_record_order_record_link.bib_record_id " +
				"INNER JOIN sierra_view.order_record_cmf ON order_record_cmf.order_record_id = order_view.id " +
				"WHERE (" + orderStatusCodesSQL + ") AND order_view.is_suppressed = 'f' AND location_code != 'multi' AND ocode4 != 'n'";
		if (serverName.contains("aurora")) {
			// Work-around for aurora order records until they take advantage of sierra acquistions in a manner we can rely on
			String auroraOrderRecordInterval = cleanIniValue(ini.get("Catalog", "auroraOrderRecordInterval"));
			if (auroraOrderRecordInterval == null || !auroraOrderRecordInterval.matches("\\d+")) {
				auroraOrderRecordInterval = "90";
			}
			activeOrderSQL += " AND NOW() - order_date_gmt < '" + auroraOrderRecordInterval + " DAY'::INTERVAL";
		} else {
			if (suppressOrderRecordsThatAreCatalogged) { // Ignore entries with a set catalog date more than a day old ( a day to allow for the transition from order item to regular item)
				activeOrderSQL += " AND (catalog_date_gmt IS NULL OR NOW() - catalog_date_gmt < '1 DAY'::INTERVAL) ";
			} else if (suppressOrderRecordsThatAreReceived) { // Ignore entries with a set received date more than a day old ( a day to allow for the transition from order item to regular item)
				activeOrderSQL += " AND (received_date_gmt IS NULL OR NOW() - received_date_gmt < '1 DAY'::INTERVAL) ";
			} else if (suppressOrderRecordsThatAreReceivedAndCatalogged) { // Only ignore entries that have both a received and catalog date, and a catalog date more than a day old
				activeOrderSQL += " AND (catalog_date_gmt IS NULL or received_date_gmt IS NULL OR NOW() - catalog_date_gmt < '1 DAY'::INTERVAL) ";
			}
		}
		try (
				PreparedStatement getActiveOrdersStmt = conn.prepareStatement(activeOrderSQL, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				ResultSet activeOrdersRS = getActiveOrdersStmt.executeQuery()
		) {
			writeToFileFromSQLResultFile(orderRecordFile, activeOrdersRS);
			activeOrdersRS.close();

			HashMap<String, Integer> updatedBibsWithOrders = new HashMap<>();
			readOrdersFile(orderRecordFile, updatedBibsWithOrders);

			//Check to see which bibs either have new or deleted orders
			for (String bibId : updatedBibsWithOrders.keySet()) {
				if (!existingBibsWithOrders.containsKey(bibId)) {
					//We didn't have a bib with an order before, update it
					allBibsToUpdate.add(bibId);
				} else {
					if (!updatedBibsWithOrders.get(bibId).equals(existingBibsWithOrders.get(bibId))) {
						//Number of orders has changed, we should reindex.
						allBibsToUpdate.add(bibId);
					}
					existingBibsWithOrders.remove(bibId);
				}

				//Now that all updated bibs are processed, look for any that we used to have that no longer exist
				allBibsToUpdate.addAll(existingBibsWithOrders.keySet());
			}
		} catch (SQLException e1) {
			logger.error("Error loading active orders", e1);
		}
		addNoteToExportLog("Finished exporting active orders");
	}

	private static void readOrdersFile(File orderRecordFile, HashMap<String, Integer> bibsWithOrders) throws IOException {
		if (orderRecordFile.exists()) {
			CSVReader orderReader = new CSVReader(new FileReader(orderRecordFile));
			//Skip the header
			orderReader.readNext();
			String[] recordData = orderReader.readNext();
			while (recordData != null) {
				if (bibsWithOrders.containsKey(recordData[0])) {
					bibsWithOrders.put(recordData[0], bibsWithOrders.get(recordData[0]) + 1);
				} else {
					bibsWithOrders.put(recordData[0], 1);
				}

				recordData = orderReader.readNext();
			}
			orderReader.close();
		}
	}

	private static Ini loadConfigFile(String filename) {
		//First load the default config file
		String configName = "../../sites/default/conf/" + filename;
		logger.info("Loading configuration from " + configName);
		File configFile = new File(configName);
		if (!configFile.exists()) {
			logger.error("Could not find configuration file " + configName);
			System.exit(1);
		}

		// Parse the configuration file
		Ini ini = new Ini();
		try {
			ini.load(new FileReader(configFile));
		} catch (InvalidFileFormatException e) {
			logger.error("Configuration file is not valid.  Please check the syntax of the file.", e);
		} catch (FileNotFoundException e) {
			logger.error("Configuration file could not be found.  You must supply a configuration file in conf called config.ini.", e);
		} catch (IOException e) {
			logger.error("Configuration file could not be read.", e);
		}

		//Now override with the site specific configuration
		String siteSpecificFilename = "../../sites/" + serverName + "/conf/" + filename;
		logger.info("Loading site specific config from " + siteSpecificFilename);
		File siteSpecificFile = new File(siteSpecificFilename);
		if (!siteSpecificFile.exists()) {
			logger.error("Could not find server specific config file");
			System.exit(1);
		}
		try {
			Ini siteSpecificIni = new Ini();
			siteSpecificIni.load(new FileReader(siteSpecificFile));
			for (Section curSection : siteSpecificIni.values()) {
				for (String curKey : curSection.keySet()) {
					//logger.debug("Overriding " + curSection.getName() + " " + curKey + " " + curSection.get(curKey));
					//System.out.println("Overriding " + curSection.getName() + " " + curKey + " " + curSection.get(curKey));
					ini.put(curSection.getName(), curKey, curSection.get(curKey));
				}
			}
		} catch (InvalidFileFormatException e) {
			logger.error("Site Specific config file is not valid.  Please check the syntax of the file.", e);
		} catch (IOException e) {
			logger.error("Site Specific config file could not be read.", e);
		}

		//Now override with the site specific configuration
//		String passwordFilename = "../../sites/" + serverName + "/conf/config.pwd.ini";
		String passwordFilename = siteSpecificFilename.replaceFirst(".ini", ".pwd.ini");
		logger.info("Loading site specific config from " + passwordFilename);
		File siteSpecificPasswordFile = new File(passwordFilename);
		if (!siteSpecificPasswordFile.exists()) {
			logger.info("Could not find server specific config password file: " + passwordFilename);
		} else {
			try {
				Ini siteSpecificIni = new Ini();
				siteSpecificIni.load(new FileReader(siteSpecificPasswordFile));
				for (Section curSection : siteSpecificIni.values()) {
					for (String curKey : curSection.keySet()) {
						//logger.debug("Overriding " + curSection.getName() + " " + curKey + " " + curSection.get(curKey));
						//System.out.println("Overriding " + curSection.getName() + " " + curKey + " " + curSection.get(curKey));
						ini.put(curSection.getName(), curKey, curSection.get(curKey));
					}
				}
			} catch (InvalidFileFormatException e) {
				logger.error("Site Specific config file is not valid.  Please check the syntax of the file.", e);
			} catch (IOException e) {
				logger.error("Site Specific config file could not be read.", e);
			}
		}

		return ini;
	}

	private static String cleanIniValue(String value) {
		if (value == null) {
			return null;
		}
		value = value.trim();
		if (value.startsWith("\"")) {
			value = value.substring(1);
		}
		if (value.endsWith("\"")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}

	private static void writeToFileFromSQLResultFile(File dataFile, ResultSet dataRS) {
		try (CSVWriter dataFileWriter = new CSVWriter(new FileWriter(dataFile))) {
			dataFileWriter.writeAll(dataRS, true);
		} catch (IOException e) {
			logger.error("Error Writing File", e);
		} catch (SQLException e) {
			logger.error("SQL Error", e);
		}
	}

	private static String sierraAPIToken;
	private static String sierraAPITokenType;
	private static long   sierraAPIExpiration;

	private static boolean connectToSierraAPI(Ini configIni, String baseUrl) {
		//Check to see if we already have a valid token
		if (sierraAPIToken != null) {
			if (sierraAPIExpiration - new Date().getTime() > 0) {
				//logger.debug("token is still valid");
				return true;
			} else {
				logger.debug("Token has expired");
			}
		}
		//Connect to the API to get our token
		HttpURLConnection conn;
		try {
			URL    emptyIndexURL = new URL(baseUrl + "/token");
			String clientKey     = cleanIniValue(configIni.get("Catalog", "clientKey"));
			String clientSecret  = cleanIniValue(configIni.get("Catalog", "clientSecret"));
			String encoded       = Base64.encodeBase64String((clientKey + ":" + clientSecret).getBytes());

			conn = (HttpURLConnection) emptyIndexURL.openConnection();
			checkForSSLConnection(conn);
			conn.setReadTimeout(30000);
			conn.setConnectTimeout(30000);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
			conn.setRequestProperty("Authorization", "Basic " + encoded);
			conn.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
			wr.write("grant_type=client_credentials");
			wr.flush();
			wr.close();

			StringBuilder response;
			if (conn.getResponseCode() == 200) {
				// Get the response
				response = getTheResponse(conn.getInputStream());
				try {
					JSONObject parser = new JSONObject(response.toString());
					sierraAPIToken     = parser.getString("access_token");
					sierraAPITokenType = parser.getString("token_type");
					//logger.debug("Token expires in " + parser.getLong("expires_in") + " seconds");
					sierraAPIExpiration = new Date().getTime() + (parser.getLong("expires_in") * 1000) - 10000;
					//logger.debug("Sierra token is " + sierraAPIToken);
				} catch (JSONException jse) {
					logger.error("Error parsing response to json " + response.toString(), jse);
					return false;
				}

			} else {
				logger.error("Received error " + conn.getResponseCode() + " connecting to sierra authentication service");
				// Get any errors
				response = getTheResponse(conn.getErrorStream());
				logger.error(response);
				return false;
			}

		} catch (Exception e) {
			logger.error("Error connecting to sierra API", e);
			return false;
		}
		return true;
	}

	private static void checkForSSLConnection(HttpURLConnection conn) {
		if (conn instanceof HttpsURLConnection) {
			HttpsURLConnection sslConn = (HttpsURLConnection) conn;
			sslConn.setHostnameVerifier((hostname, session) -> {
				return true; //Do not verify host names
			});
		}
	}

	private static StringBuilder getTheResponse(InputStream inputStream) {
		StringBuilder response = new StringBuilder();
		try (BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			while ((line = rd.readLine()) != null) {
				response.append(line);
			}
		} catch (Exception e) {
			logger.warn("Error reading response :", e);
		}
		return response;
	}

	private static boolean lastCallTimedOut = false;

	private static JSONObject callSierraApiURL(Ini configIni, String baseUrl, String sierraUrl, boolean logErrors) {
		lastCallTimedOut = false;
		if (connectToSierraAPI(configIni, baseUrl)) {
			//Connect to the API to get our token
			HttpURLConnection conn;
			try {
				URL emptyIndexURL = new URL(sierraUrl);
				conn = (HttpURLConnection) emptyIndexURL.openConnection();
				checkForSSLConnection(conn);
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Accept-Charset", "UTF-8");
				conn.setRequestProperty("Authorization", sierraAPITokenType + " " + sierraAPIToken);
				conn.setRequestProperty("Accept", "application/marc-json");
				conn.setReadTimeout(20000);
				conn.setConnectTimeout(5000);

				StringBuilder response;
				if (conn.getResponseCode() == 200) {
					// Get the response
					response = getTheResponse(conn.getInputStream());
					try {
						return new JSONObject(response.toString());
					} catch (JSONException jse) {
						logger.error("Error parsing response \n" + response.toString(), jse);
						return null;
					}

				} else {
					if (logErrors) {
						logger.error("Received error " + conn.getResponseCode() + " calling sierra API " + sierraUrl);
						// Get any errors
						response = getTheResponse(conn.getErrorStream());
						logger.error("  Finished reading response");
						logger.error(response.toString());
					}
				}

			} catch (java.net.SocketTimeoutException e) {
				logger.error("Socket timeout talking to to sierra API (callSierraApiURL) " + sierraUrl + " - " + e.toString());
				lastCallTimedOut = true;
			} catch (java.net.ConnectException e) {
				logger.error("Timeout connecting to sierra API (callSierraApiURL) " + sierraUrl + " - " + e.toString());
				lastCallTimedOut = true;
			} catch (Exception e) {
				logger.error("Error loading data from sierra API (callSierraApiURL) " + sierraUrl + " - ", e);
			}
		}
		return null;
	}

	private static String getMarcFromSierraApiURL(Ini configIni, String baseUrl, String sierraUrl, boolean logErrors) {
		lastCallTimedOut = false;
		if (connectToSierraAPI(configIni, baseUrl)) {
			//Connect to the API to get our token
			HttpURLConnection conn;
			try {
				URL emptyIndexURL = new URL(sierraUrl);
				conn = (HttpURLConnection) emptyIndexURL.openConnection();
				checkForSSLConnection(conn);
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Accept-Charset", "UTF-8");
				conn.setRequestProperty("Authorization", sierraAPITokenType + " " + sierraAPIToken);
				conn.setRequestProperty("Accept", "application/marc-json");
				conn.setReadTimeout(20000);
				conn.setConnectTimeout(5000);

				StringBuilder response;
				if (conn.getResponseCode() == 200) {
					// Get the response
					response = getTheResponse(conn.getInputStream());
					return response.toString();
				} else {
					if (logErrors) {
						logger.error("Received error " + conn.getResponseCode() + " calling sierra API " + sierraUrl);
						// Get any errors
						response = getTheResponse(conn.getErrorStream());
						logger.error("  Finished reading response");
						logger.error(response.toString());
					}
				}

			} catch (java.net.SocketTimeoutException e) {
				logger.error("Socket timeout talking to to sierra API (getMarcFromSierraApiURL) " + e.toString());
				lastCallTimedOut = true;
			} catch (java.net.ConnectException e) {
				logger.error("Timeout connecting to sierra API (getMarcFromSierraApiURL) " + e.toString());
				lastCallTimedOut = true;
			} catch (Exception e) {
				logger.error("Error loading data from sierra API (getMarcFromSierraApiURL) ", e);
			}
		}
		return null;
	}

	private static JSONObject getMarcJSONFromSierraApiURL(Ini configIni, String baseUrl, String sierraUrl) {
		lastCallTimedOut = false;
		if (connectToSierraAPI(configIni, baseUrl)) {
			//Connect to the API to get our token
			HttpURLConnection conn;
			try {
				URL emptyIndexURL = new URL(sierraUrl);
				conn = (HttpURLConnection) emptyIndexURL.openConnection();
				checkForSSLConnection(conn);
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Accept-Charset", "UTF-8");
				conn.setRequestProperty("Authorization", sierraAPITokenType + " " + sierraAPIToken);
				conn.setRequestProperty("Accept", "application/marc-in-json");
				conn.setReadTimeout(20000);
				conn.setConnectTimeout(5000);

				StringBuilder response = new StringBuilder();
				if (conn.getResponseCode() == 200) {
					// Get the response
					response = getTheResponse(conn.getInputStream());
					return new JSONObject(response.toString());
				} else {
					// Get any errors
					response = getTheResponse(conn.getErrorStream());

					try {
						return new JSONObject(response.toString());
					} catch (JSONException jse) {
						logger.error("Received error " + conn.getResponseCode() + " calling sierra API " + sierraUrl);
						logger.error(response.toString());
					}
				}

			} catch (java.net.SocketTimeoutException e) {
				logger.error("Socket timeout talking to to sierra API (getMarcJSONFromSierraApiURL) " + e.toString());
				lastCallTimedOut = true;
			} catch (java.net.ConnectException e) {
				logger.error("Timeout connecting to sierra API (getMarcJSONFromSierraApiURL) " + e.toString());
				lastCallTimedOut = true;
			} catch (Exception e) {
				logger.error("Error loading data from sierra API (getMarcJSONFromSierraApiURL) ", e);
			}
		}
		return null;
	}

	/**
	 * Calculates a check digit for a III identifier
	 *
	 * @param basedId String the base id without checksum
	 * @return String the check digit
	 */
	private static String getCheckDigit(String basedId) {
		int sumOfDigits = 0;
		for (int i = 0; i < basedId.length(); i++) {
			int multiplier = ((basedId.length() + 1) - i);
			sumOfDigits += multiplier * Integer.parseInt(basedId.substring(i, i + 1));
		}
		int modValue = sumOfDigits % 11;
		if (modValue == 10) {
			return "x";
		} else {
			return Integer.toString(modValue);
		}
	}

	private static StringBuffer     notes      = new StringBuffer();
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private static void addNoteToExportLog(String note) {
		try {
			Date date = new Date();
			notes.append("<br>").append(dateFormat.format(date)).append(": ").append(note);
			addNoteToExportLogStmt.setString(1, trimLogNotes(notes.toString()));
			addNoteToExportLogStmt.setLong(2, new Date().getTime() / 1000);
			addNoteToExportLogStmt.setLong(3, exportLogId);
			addNoteToExportLogStmt.executeUpdate();
			logger.info(note);
		} catch (SQLException e) {
			logger.error("Error adding note to Export Log", e);
		}
	}

	private static String trimLogNotes(String stringToTrim) {
		if (stringToTrim == null) {
			return null;
		}
		if (stringToTrim.length() > 65535) {
			stringToTrim = stringToTrim.substring(0, 65535);
		}
		return stringToTrim.trim();
	}

	private static boolean convertConfigStringToBoolean(String configStr) {
		if (configStr != null) {
			return configStr.equalsIgnoreCase("true") || configStr.equals("1");
		}
		return false;
	}
}