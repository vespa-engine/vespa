# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_param_usage_test_app TEST
    SOURCES
    param_usage_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_param_usage_test_app COMMAND eval_param_usage_test_app)
