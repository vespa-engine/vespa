#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#
# This script will be run by the 'runtests.sh' script before
# each individual test run.

# do not produce any output, log error messages to 'pretest.err'
exec > /dev/null 2>>pretest.err

