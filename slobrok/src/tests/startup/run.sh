#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
cmd=../../apps/slobrok/slobrok

$cmd -c mumbojumbo
if [ $? -eq 1 ]; then
    exit 0
fi
exit 1
