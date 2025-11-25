#!/usr/bin/env bash
if [ $# != 2 ]; then
	echo 'needs 2 arguments: <sd/dir> <outputdir>'
	exit 1
fi

# ensure absolute paths:
old_dir=$(pwd)
if [ "$1" = "${1#/}" ]; then set "${old_dir}/$1" "$2"; fi
if [ "$2" = "${2#/}" ]; then set "$1" "${old_dir}/$2"; fi

# assumes you have your vespa checked out and built in ~/git/vespa :
cd ~/git/vespa/config-model

mvn -q exec:java \
	-Dexec.args="$*" \
	-Dexec.classpathScope="test" \
	-Dexec.mainClass=com.yahoo.schema.derived.DeriveConfigFromSchema

echo "Files generated in directory $2:"
ls -l "$2"
