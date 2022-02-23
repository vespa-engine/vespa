# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_storeonlyfeedview_test_app TEST
    SOURCES
    storeonlyfeedview_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_feedoperation
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_proton_metrics
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_storeonlyfeedview_test_app COMMAND searchcore_storeonlyfeedview_test_app)
