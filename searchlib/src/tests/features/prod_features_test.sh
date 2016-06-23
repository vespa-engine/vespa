#!/bin/sh
set -e
VESPA_LOG_TARGET=file:vlog2.txt $VALGRIND ./searchlib_prod_features_test_app
rm -rf *.dat
