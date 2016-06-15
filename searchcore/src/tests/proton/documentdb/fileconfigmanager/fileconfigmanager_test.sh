#!/bin/bash
rm -rf out
rm -rf out2
$VALGRIND ./searchcore_fileconfigmanager_test_app
