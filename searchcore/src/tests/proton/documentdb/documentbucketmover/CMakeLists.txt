# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    GTest::GTest
)
vespa_add_test(NAME searchcore_documentbucketmover_test_app COMMAND searchcore_documentbucketmover_test_app)

vespa_add_executable(searchcore_documentmover_test_app TEST
    SOURCES
    documentmover_test.cpp
    DEPENDS
    searchcore_bucketmover_test
    searchcore_server
    searchcore_test
    searchcore_feedoperation
    GTest::GTest
)
vespa_add_test(NAME searchcore_documentmover_test_app COMMAND searchcore_documentmover_test_app)
