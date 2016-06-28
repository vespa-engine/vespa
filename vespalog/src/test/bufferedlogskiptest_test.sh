#!/bin/bash
set -e
rm -f bufferedlogskiptest.log
VESPA_LOG_TARGET="file:bufferedlogskiptest.log" VESPA_LOG_LEVEL="all -spam" ./vespalog_bufferedlogskiptest_app bufferedlogskiptest.log
