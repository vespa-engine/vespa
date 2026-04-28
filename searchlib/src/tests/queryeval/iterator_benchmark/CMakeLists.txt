# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_iterator_benchmark_test_app TEST
    SOURCES
    attribute_ctx_builder.cpp
    benchmark_blueprint_factory.cpp
    benchmark_spec.cpp
    common.cpp
    disk_index_builder.cpp
    intermediate_blueprint_factory.cpp
    iterator_benchmark_test.cpp

    DEPENDS
    vespa_searchlib
    searchlib_test
    GTest::gtest
)
# Note: this should not be executed as a unit test, so the vespa_add_test() command is not specified.
