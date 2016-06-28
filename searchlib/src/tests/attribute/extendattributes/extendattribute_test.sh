#!/bin/bash
set -e
$VALGRIND ./searchlib_extendattribute_test_app
rm -rf *.dat
