#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
