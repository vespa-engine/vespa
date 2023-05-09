# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_feedview_test_app TEST
    SOURCES
    feedview_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_index
    searchcore_feedoperation
    searchcore_matching
    searchcore_attribute
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_pcommon
    searchcore_grouping
    searchcore_proton_metrics
)
vespa_add_test(NAME searchcore_feedview_test_app COMMAND searchcore_feedview_test_app)
