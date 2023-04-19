# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vsm_nearest_neighbor_field_searcher_test_app TEST
    SOURCES
    nearest_neighbor_field_searcher_test.cpp
    DEPENDS
    searchlib
    searchlib_test
    streamingvisitors
    GTest::GTest
)

vespa_add_test(NAME vsm_nearest_neighbor_field_searcher_test_app COMMAND vsm_nearest_neighbor_field_searcher_test_app)
