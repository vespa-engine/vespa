# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_documentmetastore_test_app TEST
    SOURCES
    documentmetastore_test.cpp
    DEPENDS
    searchcore_server
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_attribute
    searchcore_feedoperation
    searchcore_fconfig
    GTest::GTest
)
vespa_add_test(NAME searchcore_documentmetastore_test_app COMMAND searchcore_documentmetastore_test_app)
