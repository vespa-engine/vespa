#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
$VALGRIND ./searchlib_attributeguard_test_app
rm -rf *.dat
rm -rf *.idx
rm -rf *.weight
rm -rf clstmp
rm -rf alstmp
