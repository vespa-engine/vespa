# Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_tensor_add_operation_test_app TEST
    SOURCES
    tensor_add_operation_test.cpp
    DEPENDS
    vespaeval
    gtest
)
vespa_add_test(NAME eval_tensor_add_operation_test_app COMMAND eval_tensor_add_operation_test_app)
