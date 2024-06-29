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

vespa_add_executable(searchcore_proton_documentdb_vespatest_test_app TEST
    SOURCES
    vespatest_runner.cpp
    buckethandler_test.cpp
    combiningfeedview_test.cpp
    configurer_test.cpp
    document_scan_iterator_test.cpp
    document_subdbs_test.cpp
    documentdbconfig_test.cpp
    documentdbconfigscout_test.cpp
    feedview_test.cpp
    job_tracked_maintenance_job_test.cpp
    maintenancecontroller_test.cpp
    storeonlyfeedview_test.cpp
    DEPENDS
    searchcore_server
    searchcore_feedoperation
    searchcore_test
)
vespa_add_test(NAME searchcore_proton_documentdb_vespatest_test_app COMMAND searchcore_proton_documentdb_vespatest_test_app)

vespa_add_executable(searchcore_proton_documentdb_gtest_test_app TEST
    SOURCES
    gtest_runner.cpp
    clusterstatehandler_test.cpp
    bucketmover_common.cpp
    documentbucketmover_test.cpp
    documentmover_test.cpp
    executor_threading_service_test.cpp
    lid_space_common.cpp
    lid_space_compaction_test.cpp
    lid_space_handler_test.cpp
    lid_space_jobtest.cpp
    DEPENDS
    searchcore_server
    searchcore_feedoperation
    searchcore_test
    GTest::GTest
)
vespa_add_test(NAME searchcore_proton_documentdb_gtest_test_app COMMAND searchcore_proton_documentdb_gtest_test_app)
