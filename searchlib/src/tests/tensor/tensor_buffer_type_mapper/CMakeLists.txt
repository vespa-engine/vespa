# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_tensor_buffer_type_mapper_test_app TEST
    SOURCES
    tensor_buffer_type_mapper_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_tensor_buffer_type_mapper_test_app COMMAND searchlib_tensor_buffer_type_mapper_test_app)
