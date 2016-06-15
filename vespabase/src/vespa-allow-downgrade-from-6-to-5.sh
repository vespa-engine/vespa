#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

for f in /home/y/var/db/vespa/search/*/*/documents/*/config/config-*
do
    echo $f
    touch $f/smartsummary.cfg
done
