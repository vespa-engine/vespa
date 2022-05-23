# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_proton_app
    SOURCES
    proton.cpp
    OUTPUT_NAME vespa-proton-bin
    INSTALL sbin
    DEPENDS
    searchcore_server
    searchcore_initializer
    searchcore_reprocessing
    searchcore_index
    searchcore_persistenceengine
    searchcore_feedoperation
    searchcore_matchengine
    searchcore_summaryengine
    searchcore_matching
    searchcore_docsummary
    searchcore_attribute
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_flushengine
    searchcore_pcommon
    searchcore_grouping
    searchcore_proton_metrics
    searchcore_fconfig
    storageserver_storageapp
)
