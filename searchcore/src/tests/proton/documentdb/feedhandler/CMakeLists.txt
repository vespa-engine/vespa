# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_feedhandler_test_app TEST
    SOURCES
    feedhandler_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_bucketdb
    searchcore_persistenceengine
    searchcore_feedoperation
    searchcore_matching
    searchcore_attribute
    searchcore_pcommon
    searchcore_grouping
    searchcore_proton_metrics
)
vespa_add_test(NAME searchcore_feedhandler_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/feedhandler_test.sh
               DEPENDS searchcore_feedhandler_test_app)
