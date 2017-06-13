#!/bin/bash
set -e
$VALGRIND ./searchlib_tensorattribute_test_app
rm -rf *.dat
