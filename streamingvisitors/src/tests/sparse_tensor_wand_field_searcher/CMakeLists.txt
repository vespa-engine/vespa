# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vsm_sparse_tensor_wand_field_searcher_test_app TEST
    SOURCES
    sparse_tensor_wand_field_searcher_test.cpp
    DEPENDS
    vespa_searchlib
    searchlib_test
    vespa_streamingvisitors
    GTest::gtest
)

vespa_add_test(NAME vsm_sparse_tensor_wand_field_searcher_test_app COMMAND vsm_sparse_tensor_wand_field_searcher_test_app)
