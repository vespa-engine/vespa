# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(staging_vespalib_process_memory_stats_test_app
    SOURCES
    process_memory_stats_test.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_process_memory_stats_test_app COMMAND sh process_memory_stats_test.sh)
