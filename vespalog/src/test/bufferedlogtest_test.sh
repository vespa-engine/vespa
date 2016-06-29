#!/bin/bash
set -e
rm -f bufferedlogtest.log 
VESPA_LOG_TARGET="file:bufferedlogtest.log" ./vespalog_bufferedlogtest_app bufferedlogtest.log
