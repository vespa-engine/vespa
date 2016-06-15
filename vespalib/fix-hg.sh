#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
td=${0%.sh}.pl
if [ -f "$td" ]; then
    find . -name '*.h' -o -name '*.hpp' | xargs $td
else
    echo "Could not find '${td}'"
fi
