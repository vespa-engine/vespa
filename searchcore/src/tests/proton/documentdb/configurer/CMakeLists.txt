# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_configurer_test_app TEST
    SOURCES
    configurer_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_reprocessing
    searchcore_index
    searchcore_docsummary
    searchcore_feedoperation
    searchcore_matching
    searchcore_attribute
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_flushengine
    searchcore_pcommon
    searchcore_grouping
    searchcore_proton_metrics
)
vespa_add_test(NAME searchcore_configurer_test_app COMMAND searchcore_configurer_test_app)
