#!/bin/sh
set -x
odir=`pwd`
if [ $# != 2 ]; then
	echo "needs sd/dir outputdir arguments"
	exit 1
fi
if [ "$1" = "${1#/}" ]; then
	set $odir/$1 $2
fi
if [ "$2" = "${2#/}" ]; then
	set $1 $odir/$2
fi
cd ~/git/vespa/config-model
mvn exec:java -Dexec.mainClass=com.yahoo.schema.derived.DeriveConfigFromSchema -Dexec.classpathScope="test" -Dexec.args="$*"
ls -l $2
