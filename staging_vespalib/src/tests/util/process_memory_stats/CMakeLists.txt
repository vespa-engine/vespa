# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(staging_vespalib_process_memory_stats_test_app TEST
    SOURCES
    process_memory_stats_test.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_process_memory_stats_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/process_memory_stats_test.sh
               DEPENDS staging_vespalib_process_memory_stats_test_app)
