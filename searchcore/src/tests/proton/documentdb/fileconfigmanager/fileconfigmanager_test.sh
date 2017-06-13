#!/bin/bash
set -e
rm -rf out
rm -rf out2
$VALGRIND ./searchcore_fileconfigmanager_test_app
