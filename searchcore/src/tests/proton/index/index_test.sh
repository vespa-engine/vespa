#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

$VALGRIND ./searchcore_diskindexcleaner_test_app
$VALGRIND ./searchcore_fusionrunner_test_app
$VALGRIND ./searchcore_indexcollection_test_app
$VALGRIND ./searchcore_indexmanager_test_app
