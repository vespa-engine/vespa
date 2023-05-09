# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_documentretriever_test_app TEST
    SOURCES
    documentretriever_test.cpp
    DEPENDS
    searchcore_server
    searchcore_attribute
    searchcore_feedoperation
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_pcommon
    searchcore_persistenceengine
)
vespa_add_test(NAME searchcore_documentretriever_test_app COMMAND searchcore_documentretriever_test_app)
vespa_add_executable(searchcore_feeddebugger_test_app TEST
    SOURCES
    feeddebugger_test.cpp
    DEPENDS
    searchcore_server
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_feeddebugger_test_app COMMAND searchcore_feeddebugger_test_app)
vespa_add_executable(searchcore_feedstates_test_app TEST
    SOURCES
    feedstates_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_bucketdb
    searchcore_persistenceengine
    searchcore_feedoperation
    searchcore_pcommon
    searchcore_proton_metrics
)
vespa_add_test(NAME searchcore_feedstates_test_app COMMAND searchcore_feedstates_test_app)
vespa_add_executable(searchcore_memoryconfigstore_test_app TEST
    SOURCES
    memoryconfigstore_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_memoryconfigstore_test_app COMMAND searchcore_memoryconfigstore_test_app)
