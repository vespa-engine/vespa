#!/bin/bash
set -e
$VALGRIND ./searchcore_documentmetastore_test_app
rm -rf documentmetastore*.dat
rm -rf dmsflush
