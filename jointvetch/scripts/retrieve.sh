#!/bin/bash

LOCALBASEDIR=/Users/Theodore/Documents
REMOTEBASEDIR=/home/mcrawford
REMOTERESULTS=/home/mcrawford/results
RESULTSSAVEDIR=/Users/Theodore/Documents/Google_Drive/SJV_EcologicalModelling_Paper/Analysis/Adjustment_Factor_Optimization
SSH="ssh -p 21212 mcrawford@199.111.89.20"

#while true
#do
#	JAVAPROCCOUNT=$($SSH "ps aux -u mcrawford | grep java | grep jointvetch | wc -l")
#	if [[ $JAVAPROCCOUNT -eq 0 ]]
#	then
		echo "Bringing over result archive"
		$SSH "cd $REMOTERESULTS && tar cvfj - ./" > $RESULTSSAVEDIR/results.tar.bz2

		echo "Removing local temp folder"
		rm -Rfv ./tmp

		echo "Ending all processes on the server"
		cd $LOCALBASEDIR/jointvetch/jointvetch/scripts
		./suicide

		echo "Unarchiving results"
		cd $RESULTSSAVEDIR
		tar -xvf results.tar.bz2
		exit 0
#	else
		echo "Sleeping!"
		sleep 60
#	fi
#done
