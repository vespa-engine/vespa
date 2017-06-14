#!/bin/bash
set -e
rm -rf test_output
$VALGRIND ./searchcore_attribute_test_app
