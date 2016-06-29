#!/bin/bash
set -e
$VALGRIND ./searchlib_attributeguard_test_app
rm -rf *.dat
rm -rf *.idx
rm -rf *.weight
rm -rf clstmp
rm -rf alstmp
