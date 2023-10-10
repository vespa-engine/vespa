#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
rm -f bufferedlogskiptest.log
VESPA_LOG_TARGET="file:bufferedlogskiptest.log" VESPA_LOG_LEVEL="all -spam" ./vespalog_bufferedlogskiptest_app bufferedlogskiptest.log
