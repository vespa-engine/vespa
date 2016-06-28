#!/bin/bash
set -e
$VALGRIND ./searchlib_searchcontext_test_app
rm -rf *.dat
rm -rf *.idx
rm -rf *.weight
