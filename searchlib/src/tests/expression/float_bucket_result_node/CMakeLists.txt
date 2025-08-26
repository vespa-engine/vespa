# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_test_executable(searchlib_float_bucket_result_node_test_app TEST
    SOURCES
    float_bucket_result_node_test.cpp
    DEPENDS
    vespa_searchlib
    searchlib_test
)
