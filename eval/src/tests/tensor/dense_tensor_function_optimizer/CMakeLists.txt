# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_dense_tensor_function_optimizer_test_app TEST
    SOURCES
    dense_tensor_function_optimizer_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_dense_tensor_function_optimizer_test_app COMMAND eval_dense_tensor_function_optimizer_test_app)
