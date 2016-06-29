#!/bin/bash
set -e
rm -f mapfile
$VALGRIND ./staging_vespalib_process_memory_stats_test_app
rm -f mapfile
