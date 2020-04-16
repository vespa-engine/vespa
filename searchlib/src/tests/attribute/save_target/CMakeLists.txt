# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_attribute_save_target_test_app TEST
    SOURCES
    attribute_save_target_test.cpp
    DEPENDS
    searchlib
    gtest
)
vespa_add_test(NAME searchlib_attribute_save_target_test_app COMMAND searchlib_attribute_save_target_test_app)
