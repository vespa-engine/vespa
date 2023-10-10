#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
rm -f bufferedlogtest.log 
VESPA_LOG_TARGET="file:bufferedlogtest.log" ./vespalog_bufferedlogtest_app bufferedlogtest.log
