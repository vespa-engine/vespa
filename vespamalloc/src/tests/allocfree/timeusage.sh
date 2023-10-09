#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
grep "CPU" |grep "elapsed" | sed "s/user / user /g" | sed "s/system / system /g" | sed "s/elapsed / elapsed /g" | sed "s/CPU / CPU /g" | awk '{print $1 ";\t" $3 ";\t" $7 ";"}'
