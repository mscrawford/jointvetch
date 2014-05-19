#!/bin/bash
LOCALBASEDIR=/Users/Theodore/Documents
REMOTEBASEDIR=/home/mcrawford
REMOTERESULTS=/home/mcrawford/results
RESULTSSAVEDIR=/Users/Theodore/Desktop/results
SSH="ssh -p 21212 mcrawford@199.111.89.20"

echo "Suicide"
cd $LOCALBASEDIR/jointvetch/jointvetch/scripts
./suicide.sh

echo "Removing .class files from server"
$SSH "cd $REMOTEBASEDIR/jointvetch/jointvetch && \
rm *.class &&
rm runs.sh"

echo "Making local temp directory."
cd $LOCALBASEDIR/jointvetch/jointvetch
mkdir tmp

echo "Compiling java code to temp directory"
javac -d ./tmp *.java

echo "Running python job builder"
cd $LOCALBASEDIR/jointvetch/jointvetch/scripts
python runscript.py

echo "scp-ing java files and jobs to server"
cd $LOCALBASEDIR/jointvetch/jointvetch
scp -P 21212 ./tmp/jointvetch/*.class mcrawford@199.111.89.20:$REMOTEBASEDIR/jointvetch/jointvetch
scp -P 21212 ./tmp/runs.sh mcrawford@199.111.89.20:$REMOTEBASEDIR/jointvetch/jointvetch

DATE=$(date +%Y%m%d-%H%M)

$SSH "if [ -d $REMOTERESULTS ] ; then \
    mkdir $REMOTEBASEDIR/archive/$DATE-results ; \
    mv $REMOTERESULTS/* $REMOTEBASEDIR/archive/$DATE-results/ ; \
    fi"

echo "Running simulation"
$SSH "cd $REMOTEBASEDIR/jointvetch/jointvetch \
    && nohup xjobs -s $REMOTEBASEDIR/jointvetch/jointvetch/runs.sh \
    -j 8 > log.out 2> log.err < /dev/null &"

cd $LOCALBASEDIR/jointvetch/jointvetch/scripts

