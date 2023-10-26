#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
VESPA_LOG_TARGET=file:vlog2.txt $VALGRIND ./searchlib_prod_features_test_app
rm -rf *.dat
