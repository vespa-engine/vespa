# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_inline_operation_test_app TEST
    SOURCES
    inline_operation_test.cpp
    DEPENDS
    vespaeval
    gtest
)
vespa_add_test(NAME eval_inline_operation_test_app COMMAND eval_inline_operation_test_app)
