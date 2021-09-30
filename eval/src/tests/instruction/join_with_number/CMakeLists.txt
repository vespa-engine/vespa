# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_join_with_number_function_test_app TEST
    SOURCES
    join_with_number_function_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_join_with_number_function_test_app COMMAND eval_join_with_number_function_test_app)
