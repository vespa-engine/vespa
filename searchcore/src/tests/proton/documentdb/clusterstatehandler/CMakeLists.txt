# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_clusterstatehandler_test_app TEST
    SOURCES
    clusterstatehandler_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_feedoperation
    searchcore_matching
    searchcore_attribute
    searchcore_pcommon
    searchcore_grouping
)
vespa_add_test(NAME searchcore_clusterstatehandler_test_app COMMAND searchcore_clusterstatehandler_test_app)
