# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_memory_index_test_app TEST
    SOURCES
    memory_index_test.cpp
    DEPENDS
    searchlib_test
    GTest::GTest
)
vespa_add_test(NAME searchlib_memory_index_test_app COMMAND searchlib_memory_index_test_app)
