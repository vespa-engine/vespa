#!/bin/bash
set -e

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

. ../../binref/env.sh

$BINREF/compilejava $SOURCE_DIRECTORY/TestCompile.java
$BINREF/runjava TestCompile

