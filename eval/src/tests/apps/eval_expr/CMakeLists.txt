# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_eval_expr_test_app TEST
    SOURCES
    eval_expr_test.cpp
    DEPENDS
    vespaeval
    AFTER
    eval_eval_expr_app
)
vespa_add_test(NAME eval_eval_expr_test_app COMMAND eval_eval_expr_test_app)
