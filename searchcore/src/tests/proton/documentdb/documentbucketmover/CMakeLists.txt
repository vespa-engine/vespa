# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_library(searchcore_bucketmover_test STATIC
    SOURCES
    bucketmover_common.cpp
)

vespa_add_executable(searchcore_documentbucketmover_test_app TEST
    SOURCES
    documentbucketmover_test.cpp
    DEPENDS
    searchcore_bucketmover_test
    searchcore_test
    searchcore_server
    searchcore_feedoperation
    #searchcore_persistenceengine
    #searchcore_matching
    #searchcore_attribute
    #searchcore_documentmetastore
    #searchcore_bucketdb
    #searchcore_pcommon
    #searchcore_grouping
    #searchcore_fconfig
)
vespa_add_test(NAME searchcore_documentbucketmover_test_app COMMAND searchcore_documentbucketmover_test_app)

vespa_add_executable(searchcore_scaniterator_test_app TEST
    SOURCES
    scaniterator_test.cpp
    DEPENDS
    searchcore_bucketmover_test
    searchcore_server
    searchcore_test
    searchcore_feedoperation
)
vespa_add_test(NAME searchcore_scaniterator_test_app COMMAND searchcore_scaniterator_test_app)
