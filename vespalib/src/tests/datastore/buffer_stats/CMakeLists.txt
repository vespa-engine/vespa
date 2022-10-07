# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_datastore_buffer_stats_test_app TEST
    SOURCES
    buffer_stats_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_datastore_buffer_stats_test_app COMMAND vespalib_datastore_buffer_stats_test_app)
