#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

export CCACHE_MAXSIZE="1250M"
export CCACHE_COMPRESS=1
ccache --print-config
