#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
rm -f mapfile
$VALGRIND ./vespalib_process_memory_stats_test_app
rm -f mapfile
