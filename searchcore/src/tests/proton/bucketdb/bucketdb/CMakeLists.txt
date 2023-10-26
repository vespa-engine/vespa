# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_bucketdb_test_app TEST
    SOURCES
    bucketdb_test.cpp
    DEPENDS
    searchcore_bucketdb
)
vespa_add_test(NAME searchcore_bucketdb_test_app COMMAND searchcore_bucketdb_test_app)
