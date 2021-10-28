# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_unique_store_string_allocator_test_app TEST
    SOURCES
    unique_store_string_allocator_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_unique_store_string_allocator_test_app COMMAND vespalib_unique_store_string_allocator_test_app)
