# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_persistenceconformance_test_app TEST
    SOURCES
    persistenceconformance_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_initializer
    searchcore_reprocessing
    searchcore_index
    searchcore_persistenceengine
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
    searchcore_fconfig
    vdstestlib
    persistence_persistence_conformancetest
)
