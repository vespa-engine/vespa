#!/bin/bash
set -e
rm -rf tmp
rm -rf tmpdb
rm -rf summary
rm -rf indexingdocument
rm -rf searchdocument
rm -rf *.dat
$VALGRIND ./searchcore_docsummary_test_app
rm -rf tmp
rm -rf tmpdb
rm -rf summary
rm -rf indexingdocument
rm -rf searchdocument
rm -rf *.dat
$VALGRIND ./searchcore_summaryfieldconverter_test_app
