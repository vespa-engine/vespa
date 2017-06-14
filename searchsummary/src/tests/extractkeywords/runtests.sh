#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#
# $Id$
#
# Copyright (C) 2000-2003 Fast Search & Transfer ASA
# Copyright (C) 2003 Overture Services Norway AS
#
# All Rights Reserved
#
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
