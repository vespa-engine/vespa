# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_array_store_dynamic_type_mapper_test_app TEST
    SOURCES
    array_store_dynamic_type_mapper_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_array_store_dynamic_type_mapper_test_app COMMAND vespalib_array_store_dynamic_type_mapper_test_app)
