# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_documentdb_test_app TEST
    SOURCES
    documentdb_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_initializer
    searchcore_reprocessing
    searchcore_index
    searchcore_docsummary
    searchcore_persistenceengine
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
vespa_add_test(NAME searchcore_documentdb_test_app COMMAND searchcore_documentdb_test_app)

vespa_add_executable(searchcore_proton_documentdb_vespa_test_app TEST
    SOURCES
    vespa_testrunner.cpp
    buckethandler_test.cpp
    combiningfeedview_test.cpp
    configurer_test.cpp
    feedview_test.cpp
    maintenancecontroller_test.cpp
    storeonlyfeedview_test.cpp
    DEPENDS
    searchcore_server
    searchcore_feedoperation
    searchcore_test
)
vespa_add_test(NAME searchcore_proton_documentdb_vespa_test_app COMMAND searchcore_proton_documentdb_vespa_test_app)
