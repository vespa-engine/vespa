# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_map_lookup_sort_blob_writer_test_app TEST
    SOURCES
    map_lookup_sort_blob_writer_test.cpp
    DEPENDS
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_map_lookup_sort_blob_writer_test_app COMMAND searchlib_map_lookup_sort_blob_writer_test_app)
