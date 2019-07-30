#!/usr/bin/env bash

EMAIL=root@titan
PIKASERVER=marmot.test

OUTPUT_FILE="/var/log/vufind-plus/${PIKASERVER}/overdrive_continuous_reindex_output.log"

source "/usr/local/vufind-plus/vufind/bash/checkConflicts.sh"

function sendEmail() {
	# add any logic wanted for when to send the emails here. (eg errors only)
	FILESIZE=$(stat -c%s ${OUTPUT_FILE})
	if [[ ${FILESIZE} -gt 0 ]]
	then
			# send mail
			mail -s "Continuous Extract and Reindexing - ${PIKASERVER}" $EMAIL < ${OUTPUT_FILE}
	fi
}

function checkForDBCrash() {
# Pass this function the exit code ($?) of pika java programs.
# If the exit code is zero that indicates that the pika database is down or unreachable,
# so we will pause our operations here
	EXITCODE=$1
	if [ $EXITCODE -eq 2 ];then
		sleep 180
		echo "Received database connection lost error, paused for 180 seconds" >> ${OUTPUT_FILE}
	fi
}

while true
do
	#####
	# Check to make sure this is a good time to run.
	#####

	# Make sure we are not running a Full Record Group/Reindex process
	hasConflicts=$(checkConflictingProcesses "full_update.sh")
	#If we did get a conflict, restart the loop to make sure that all tests run
	if (($? != 0)); then
		continue
	fi

##TODO: Does this matter with the sierra api extract now?
#	# Do not run while the export from Sierra is running to prevent inconsistencies with MARC records
#	# export starts at 10 pm the file is copied to the FTP server at about 11:40
#	hasConflicts=$(checkProhibitedTimes "21:50" "23:40")
#	#If we did get a conflict, restart the loop to make sure that all tests run
#	if (($? != 0)); then
#		continue
#	fi

	#####
	# Start of the actual indexing code
	#####

	# reset the output file each round
	: > $OUTPUT_FILE;

	#export from overdrive
	cd /usr/local/vufind-plus/vufind/overdrive_api_extract/
	nice -n -10 java -server -XX:+UseG1GC -jar overdrive_extract.jar ${PIKASERVER} >> ${OUTPUT_FILE}

	# Pause if another reindexer is running; check in 10 second intervals
	paused=$(checkConflictingProcesses "reindexer.jar" 10)
	# push output into a variable to avoid so it doesn't echo out of the script

	#run reindex
	cd /usr/local/vufind-plus/vufind/reindexer
	nice -n -5 java -server -XX:+UseG1GC -jar reindexer.jar ${PIKASERVER} >> ${OUTPUT_FILE}
	checkForDBCrash $?

	# send notice of any issues
	sendEmail

		#end block
done
