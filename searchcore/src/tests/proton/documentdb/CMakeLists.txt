# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_documentdb_test_app
    SOURCES
    documentdb_test.cpp
    DEPENDS
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
    searchcore_fconfig
    searchcore_util
)
vespa_add_test(NAME searchcore_documentdb_test_app COMMAND sh documentdb_test.sh)
