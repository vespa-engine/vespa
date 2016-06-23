#!/bin/bash
set -e
rm -rf flush
$VALGRIND ./searchcore_attributeflush_test_app
