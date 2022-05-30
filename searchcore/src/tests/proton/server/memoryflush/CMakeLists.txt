# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_memoryflush_test_app TEST
    SOURCES
    memoryflush_test.cpp
    DEPENDS
    searchcore_server
    searchcore_flushengine
    searchcore_test
)
vespa_add_test(NAME searchcore_memoryflush_test_app COMMAND searchcore_memoryflush_test_app)
