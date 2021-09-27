# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_bmcluster_estimate_moved_docs_ratio_test_app TEST
    SOURCES
    estimate_moved_docs_ratio_test.cpp
    DEPENDS
    searchcore_bmcluster
    GTest::GTest
)

vespa_add_test(NAME searchcore_bmcluster_estimate_moved_docs_ratio_test_app
    COMMAND searchcore_bmcluster_estimate_moved_docs_ratio_test_app)
