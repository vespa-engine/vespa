# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(searchlib_query_streaming_gtest_runner_app TEST
    SOURCES
    equiv_query_node_test.cpp
    gtest_runner.cpp
    hit_iterator_test.cpp
    hit_iterator_pack_test.cpp
    near_test.cpp
    phrase_query_node_test.cpp
    query_builder_test.cpp
    same_element_query_node_test.cpp
    DEPENDS
    searchlib_queryeval_test
    vespa_searchlib
    GTest::gtest
)

vespa_add_test(NAME searchlib_query_streaming_gtest_runner_app COMMAND searchlib_query_streaming_gtest_runner_app)

