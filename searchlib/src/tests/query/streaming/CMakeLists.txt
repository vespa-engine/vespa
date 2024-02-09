# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(searchlib_query_streaming_equiv_query_node_test_app TEST
    SOURCES
    equiv_query_node_test.cpp
    DEPENDS
    searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_query_streaming_equiv_query_node_test_app COMMAND searchlib_query_streaming_equiv_query_node_test_app)

vespa_add_executable(searchlib_query_streaming_hit_iterator_test_app TEST
    SOURCES
    hit_iterator_test.cpp
    DEPENDS
    searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_query_streaming_hit_iterator_test_app COMMAND searchlib_query_streaming_hit_iterator_test_app)

vespa_add_executable(searchlib_query_streaming_hit_iterator_pack_test_app TEST
    SOURCES
    hit_iterator_pack_test.cpp
    DEPENDS
    searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_query_streaming_hit_iterator_pack_test_app COMMAND searchlib_query_streaming_hit_iterator_pack_test_app)

vespa_add_executable(searchlib_query_streaming_near_test_app TEST
    SOURCES
    near_test.cpp
    DEPENDS
    searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_query_streaming_near_test_app COMMAND searchlib_query_streaming_near_test_app)

vespa_add_executable(searchlib_query_streaming_same_element_query_node_test_app TEST
    SOURCES
    same_element_query_node_test.cpp
    DEPENDS
    searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_query_streaming_same_element_query_node_test_app COMMAND searchlib_query_streaming_same_element_query_node_test_app)

vespa_add_executable(searchlib_query_streaming_phrase_query_node_test_app TEST
    SOURCES
    phrase_query_node_test.cpp
    DEPENDS
    searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_query_streaming_phrase_query_node_test_app COMMAND searchlib_query_streaming_phrase_query_node_test_app)
