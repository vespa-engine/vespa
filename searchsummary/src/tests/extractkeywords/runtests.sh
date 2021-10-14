#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

if $VALGRIND ./searchsummary_extractkeywordstest_app -
then
  :
else
  echo FAILED: searchsummary_extractkeywordstest_app test failed
  exit 1
fi

if $VALGRIND ./searchsummary_extractkeywordstest_app - '*1000'
then
  :
else
  echo FAILED: searchsummary_extractkeywordstest_app test failed
  exit 1
fi

echo SUCCESS: searchsummary_extractkeywordstest_app test completed
