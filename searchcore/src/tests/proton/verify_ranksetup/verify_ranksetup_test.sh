#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

export PWD=$(pwd)
$VALGRIND ./searchcore_verify_ranksetup_test_app
