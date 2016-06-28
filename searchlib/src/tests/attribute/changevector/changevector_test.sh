#!/bin/bash
set -e
$VALGRIND ./searchlib_changevector_test_app
rm -rf *.dat
rm -rf *.idx
rm -rf *.weight
rm -rf clstmp
rm -rf alstmp
