#!/bin/bash

# full_update.sh
# Mark Noble, Marmot Library Network
# James Staub, Nashville Public Library
# 20150219
# Script handles all aspects of a full index including extracting data from other systems.
# Should be called once per day from crontab
# For Pika discovery partners using Millennium 2011 1.6_3

# this version emails script output as a round finishes
EMAIL=pikaservers@marmot.org
PIKASERVER=arlington.test
PIKADBNAME=pika
OUTPUT_FILE="/var/log/vufind-plus/${PIKASERVER}/full_update_output.log"

MINFILE1SIZE=$((505000000))

# Check if full_update is already running (test sites only)
PIDFILE="/var/log/vufind-plus/${PIKASERVER}/full_update.pid"
if [ -f $PIDFILE ]
then
	PID=$(cat $PIDFILE)
	ps -p $PID > /dev/null 2>&1
	if [ $? -eq 0 ]
	then
		mail -s "Full Extract and Reindexing - ${PIKASERVER}" $EMAIL <<< "$0 is already running"
		exit 1
	else
		## Process not found assume not running
		echo $$ > $PIDFILE
		if [ $? -ne 0 ]
		then
			mail -s "Full Extract and Reindexing - ${PIKASERVER}" $EMAIL <<< "Could not create PID file for $0"
			exit 1
		fi
	fi
else
	echo $$ > $PIDFILE
	if [ $? -ne 0 ]
	then
		mail -s "Full Extract and Reindexing - ${PIKASERVER}" $EMAIL <<< "Could not create PID file for $0"
		exit 1
	fi
fi

# Check for conflicting processes currently running
function checkConflictingProcesses() {
	#Check to see if the conflict exists.
	countConflictingProcesses=$(ps aux | grep -v sudo | grep -c "$1")
	countConflictingProcesses=$((countConflictingProcesses-1))

	let numInitialConflicts=countConflictingProcesses
	#Wait until the conflict is gone.
	until ((${countConflictingProcesses} == 0)); do
		countConflictingProcesses=$(ps aux | grep -v sudo | grep -c "$1")
		countConflictingProcesses=$((countConflictingProcesses-1))
		#echo "Count of conflicting process" $1 $countConflictingProcesses
		sleep 300
	done
	#Return the number of conflicts we found initially.
	echo ${numInitialConflicts};
}

# Prohibited time ranges - for, e.g., ILS backup
# JAMES is currently giving all Nashville prohibited times a ten minute buffer
function checkProhibitedTimes() {
	start=$(date --date=$1 +%s)
	stop=$(date --date=$2 +%s)
	NOW=$(date +%H:%M:%S)
	NOW=$(date --date=$NOW +%s)

	hasConflicts=0
	if (( $start < $stop ))
	then
		if (( $NOW > $start && $NOW < $stop ))
		then
			#echo "Sleeping:" $(($stop - $NOW))
			sleep $(($stop - $NOW))
			hasConflicts=1
		fi
	elif (( $start > $stop ))
	then
		if (( $NOW < $stop ))
		then
			sleep $(($stop - $NOW))
			hasConflicts=1
		elif (( $NOW > $start ))
		then
			sleep $(($stop + 86400 - $NOW))
			hasConflicts=1
		fi
	fi
	echo ${hasConflicts};
}

#First make sure that we aren't running at a bad time.  This is really here in case we run manually.
# since the run in cron is timed to avoid sensitive times.
# Arlington has no prohibited times (yet)
#checkProhibitedTimes "23:50" "00:40"

#Check for any conflicting processes that we shouldn't do a full index during.
#Since we aren't running in a loop, check in the order they run.
checkConflictingProcesses "sierra_export.jar ${PIKASERVER}"
checkConflictingProcesses "overdrive_extract.jar ${PIKASERVER}"
checkConflictingProcesses "reindexer.jar ${PIKASERVER}"

#truncate the output file so you don't spend a week debugging an error from a week ago!
: > $OUTPUT_FILE;

# Back-up Solr Master Index
mysqldump ${PIKADBNAME} grouped_work_primary_identifiers > /data/vufind-plus/${PIKASERVER}/grouped_work_primary_identifiers.sql
sleep 2m
tar -czf /data/vufind-plus/${PIKASERVER}/solr_master_backup.tar.gz /data/vufind-plus/${PIKASERVER}/solr_master/grouped/index/ /data/vufind-plus/${PIKASERVER}/grouped_work_primary_identifiers.sql >> ${OUTPUT_FILE}
rm /data/vufind-plus/${PIKASERVER}/grouped_work_primary_identifiers.sql

#Restart Solr
cd /usr/local/vufind-plus/sites/${PIKASERVER}; ./${PIKASERVER}.sh restart

#Extract from ILS
#Copy extracts from FTP Server
#/root/cron/copyArlingtonExport.sh >> ${OUTPUT_FILE}

# Get Yesterday's Full Export From Arlington Production Server
YESTERDAY=$(date -d "yesterday 13:00 " +"%m_%d_%Y")
scp -Cqp -i /root/.ssh/id_rsa sierraftp@158.59.15.152:/data/vufind-plus/arlington.production/marc/pika1.mrc /data/vufind-plus/arlington.test/marc/pika1.mrc >> ${OUTPUT_FILE}
#scp -Cqp -i /root/.ssh/id_rsa sierraftp@158.59.15.152:/data/vufind-plus/arlington.production/marc_export/pika1.$YESTERDAY.mrc /data/vufind-plus/arlington.test/marc/pika1.mrc >> ${OUTPUT_FILE}
#scp -Cqp -i /root/.ssh/id_rsa sierraftp@158.59.15.152:/data/vufind-plus/arlington.production/marc_export/pika2.$YESTERDAY.mrc /data/vufind-plus/arlington.test/marc/pika2.mrc >> ${OUTPUT_FILE}

#Extract from Hoopla
# (Arlington maintains the Hoopla record set within their ILS)
#cd /usr/local/vufind-plus/vufind/cron;./HOOPLA.sh ${PIKASERVER} >> ${OUTPUT_FILE}

#Extract Lexile Data
#cd /data/vufind-plus/; wget -N --no-verbose https://cassini.marmot.org/lexileTitles.txt
cd /data/vufind-plus/; curl --remote-name --remote-time --silent --show-error --compressed --time-cond /data/vufind-plus/lexileTitles.txt https://cassini.marmot.org/lexileTitles.txt

#Extract AR Data
#cd /data/vufind-plus/accelerated_reader; wget -N --no-verbose https://cassini.marmot.org/RLI-ARDataTAB.txt
cd /data/vufind-plus/accelerated_reader; curl --remote-name --remote-time --silent --show-error --compressed --time-cond /data/vufind-plus/accelerated_reader/RLI-ARDataTAB.txt https://cassini.marmot.org/RLI-ARDataTAB.txt


#Do a full extract from OverDrive just once a week to catch anything that doesn't
#get caught in the regular extract
DAYOFWEEK=$(date +"%u")
if [ "${DAYOFWEEK}" -eq 6 ];
then
	cd /usr/local/vufind-plus/vufind/overdrive_api_extract/
	nice -n -10 java -jar overdrive_extract.jar ${PIKASERVER} fullReload >> ${OUTPUT_FILE}
fi

FILE1="/data/vufind-plus/arlington.test/marc/pika1.mrc"
#FILE2="/data/vufind-plus/arlington.test/marc/pika2.mrc"
if [ -n "$FILE1" ]
then
#	if [ -n "$FILE2" ]
#	then

		FILE1SIZE=$(wc -c <"$FILE1")
		if [ $FILE1SIZE -ge $MINFILE1SIZE ]; then
#		 FILE2SIZE=$(wc -c <"$FILE2")
#		 if [ $FILE2SIZE -ge $MINFILE2SIZE ]; then

			echo "Latest file (1) is " $FILE1 >> ${OUTPUT_FILE}
			DIFF=$(($FILE1SIZE - $MINFILE1SIZE))
			PERCENTABOVE=$((100 * $DIFF / $MINFILE1SIZE))
			echo "The export file (1) is $PERCENTABOVE (%) larger than the minimum size check." >> ${OUTPUT_FILE}

#			echo "Latest file (2) is " $FILE2 >> ${OUTPUT_FILE}
#			DIFF=$(($FILE2SIZE - $MINFILE2SIZE))
#			PERCENTABOVE=$((100 * $DIFF / $MINFILE2SIZE))
#			echo "The export file (2) is $PERCENTABOVE (%) larger than the minimum size check." >> ${OUTPUT_FILE}


			#Get the updated volume information
			cd /usr/local/vufind-plus/vufind/cron; nice -n -10 java -jar cron.jar ${PIKASERVER} ExportSierraData >> ${OUTPUT_FILE}

			#Validate the export
			cd /usr/local/vufind-plus/vufind/cron; java -server -XX:+UseG1GC -jar cron.jar ${PIKASERVER} ValidateMarcExport >> ${OUTPUT_FILE}

			#Full Regroup
			cd /usr/local/vufind-plus/vufind/record_grouping; java -server -XX:+UseG1GC -Xmx2G -jar record_grouping.jar ${PIKASERVER} fullRegroupingNoClear >> ${OUTPUT_FILE}

			#Full Reindex
			cd /usr/local/vufind-plus/vufind/reindexer; java -server -XX:+UseG1GC -Xmx2G -jar reindexer.jar ${PIKASERVER} fullReindex >> ${OUTPUT_FILE}

			# Truncate Continous Reindexing list of changed items
			cat /dev/null >| /data/vufind-plus/${PIKASERVER}/marc/changed_items_to_process.csv

			#Remove all ITEM_UPDATE_EXTRACT_PIKA files so continuous_partial_reindex can start fresh
			find /data/vufind-plus/${PIKASERVER}/marc -name 'ITEM_UPDATE_EXTRACT_PIKA*' -delete
			#TODO: Don't think this is needed any more

			# Wait 2 minutes for solr replication to finish; then delete the inactive solr indexes folders older than 48 hours
			# Note: Running in the full update because we know there is a freshly created index.
			sleep 2m
			find /data/vufind-plus/${PIKASERVER}/solr_searcher/grouped/ -name "index.*" -type d -mmin +2880 -exec rm -rf {} \; >> ${OUTPUT_FILE}

#			else
#				echo $FILE2 " size " $FILE2SIZE "is less than minimum size :" $MINFILE2SIZE "; Export was not moved to data directory." >> ${OUTPUT_FILE}
#			fi
		else
			echo $FILE1 " size " $FILE1SIZE "is less than minimum size :" $MINFILE1SIZE "; Export was not moved to data directory." >> ${OUTPUT_FILE}
		fi

#	else
#		echo "Did not find a Sierra export file (2) from the last 24 hours, Full Regrouping & Full Reindexing skipped." >> ${OUTPUT_FILE}
#	fi

else
	echo "Did not find a Sierra export file (1) from the last 24 hours, Full Regrouping & Full Reindexing skipped." >> ${OUTPUT_FILE}
fi

# Clean-up Solr Logs
find /usr/local/vufind-plus/sites/default/solr/jetty/logs -name "solr_log_*" -mtime +7 -delete
find /usr/local/vufind-plus/sites/default/solr/jetty/logs -name "solr_gc_log_*" -mtime +7 -delete

#Restart Solr
cd /usr/local/vufind-plus/sites/${PIKASERVER}; ./${PIKASERVER}.sh restart

#Email results
FILESIZE=$(stat -c%s ${OUTPUT_FILE})
if [[ ${FILESIZE} > 0 ]]
then
	# send mail
	mail -s "Full Extract and Reindexing - ${PIKASERVER}" $EMAIL < ${OUTPUT_FILE}
fi

