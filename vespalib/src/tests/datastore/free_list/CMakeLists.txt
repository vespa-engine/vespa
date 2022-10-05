# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_datastore_free_list_test_app TEST
    SOURCES
    free_list_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_datastore_free_list_test_app COMMAND vespalib_datastore_free_list_test_app)
