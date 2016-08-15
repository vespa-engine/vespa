#!/bin/bash
set -e
$VALGRIND ./searchcore_documentdb_test_app
rm -rf typea tmp
