# Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_common_struct_field_mapper_test_app TEST
    SOURCES
    struct_field_mapper_test.cpp
    DEPENDS
    searchlib
    gtest
)
vespa_add_test(NAME searchlib_common_struct_field_mapper_test_app COMMAND searchlib_common_struct_field_mapper_test_app)
