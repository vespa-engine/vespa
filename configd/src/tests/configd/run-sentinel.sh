#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if ../../apps/sentinel/vespa-config-sentinel > tmp.log 2>&1 ; then
	echo "Should need argument"
	exit 1
fi
if grep -q Usage tmp.log ; then
	: ok
else
	echo "Missing usage in log:"
	cat tmp.log
	exit 1
fi

exit 0
