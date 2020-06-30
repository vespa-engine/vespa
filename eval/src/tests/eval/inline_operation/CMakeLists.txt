# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
find_package(GTest REQUIRED)
vespa_add_executable(eval_inline_operation_test_app TEST
    SOURCES
    inline_operation_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_inline_operation_test_app COMMAND eval_inline_operation_test_app)
