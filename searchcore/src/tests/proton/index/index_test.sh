#!/bin/bash
set -e

$VALGRIND ./searchcore_diskindexcleaner_test_app
$VALGRIND ./searchcore_fusionrunner_test_app
$VALGRIND ./searchcore_indexcollection_test_app
$VALGRIND ./searchcore_indexmanager_test_app
