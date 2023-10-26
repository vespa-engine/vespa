#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
env $(make ldl) ./vbench --input input.txt --qps 2 --host www.host.com --port 80
