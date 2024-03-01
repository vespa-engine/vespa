# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_iterator_benchmark_test_app TEST
    SOURCES
    disk_index_builder.cpp
    iterator_benchmark_test.cpp
    DEPENDS
    searchlib
    searchlib_test
    GTest::GTest
)
# Note: this should not be executed as a unit test, so the vespa_add_test() command is not specified.
