#!/bin/bash
set -e
$VALGRIND ./searchlib_stringattribute_test_app
rm -rf *.dat
