#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

log_message () {
    echo "warning   $*" | logger -t vespa-core-dumper
}

compressor=$1
corefile=$2
option=$3

log_message "Starting $compressor > $corefile"


if [ -f "$corefile" ]
then
    if [ "$option" != "overwrite" ]
    then
        log_message "$corefile is read-only. Core is not dumped."
        exit 1
    else
        log_message "Overwriting $corefile"
    fi
fi

$compressor > $corefile
chmod 444 $corefile
log_message "Finished $compressor > $corefile"
