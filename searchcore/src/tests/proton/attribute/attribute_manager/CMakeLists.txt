# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_attribute_manager_test_app TEST
    SOURCES
    attribute_manager_test.cpp
    DEPENDS
    searchcore_server
    searchcore_attribute
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_initializer
    searchcore_flushengine
    searchcore_pcommon
    searchcore_test
)
vespa_add_test(NAME searchcore_attribute_manager_test_app COMMAND searchcore_attribute_manager_test_app)
