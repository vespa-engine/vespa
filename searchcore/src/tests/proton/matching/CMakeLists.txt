# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_matching_test_app TEST
    SOURCES
    matching_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_fconfig
    searchcore_matching
    searchcore_feedoperation
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_pcommon
    searchcore_grouping
    searchlib_test
)
vespa_add_test(NAME searchcore_matching_test_app COMMAND searchcore_matching_test_app)
vespa_add_executable(searchcore_sessionmanager_test_app TEST
    SOURCES
    sessionmanager_test.cpp
    DEPENDS
    searchcore_matching
    searchcore_grouping
)
vespa_add_test(NAME searchcore_sessionmanager_test_app COMMAND searchcore_sessionmanager_test_app)
vespa_add_executable(searchcore_matching_stats_test_app TEST
    SOURCES
    matching_stats_test.cpp
    DEPENDS
    searchcore_matching
)
vespa_add_test(NAME searchcore_matching_stats_test_app COMMAND searchcore_matching_stats_test_app)
vespa_add_executable(searchcore_query_test_app TEST
    SOURCES
    query_test.cpp
    DEPENDS
    searchcore_server
    searchcore_matching
)
vespa_add_test(NAME searchcore_query_test_app COMMAND searchcore_query_test_app)
vespa_add_executable(searchcore_termdataextractor_test_app TEST
    SOURCES
    termdataextractor_test.cpp
    DEPENDS
    searchcore_matching
)
vespa_add_test(NAME searchcore_termdataextractor_test_app COMMAND searchcore_termdataextractor_test_app)
vespa_add_executable(searchcore_resolveviewvisitor_test_app TEST
    SOURCES
    resolveviewvisitor_test.cpp
    DEPENDS
    searchcore_matching
)
vespa_add_test(NAME searchcore_resolveviewvisitor_test_app COMMAND searchcore_resolveviewvisitor_test_app)
vespa_add_executable(searchcore_querynodes_test_app TEST
    SOURCES
    querynodes_test.cpp
    DEPENDS
    searchcore_matching
)
vespa_add_test(NAME searchcore_querynodes_test_app COMMAND searchcore_querynodes_test_app)
