# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_dynamic_array_buffer_type_test_app TEST
    SOURCES
    dynamic_array_buffer_type_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_dynamic_array_buffer_type_test_app COMMAND vespalib_dynamic_array_buffer_type_test_app)
