# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_termwise_eval_test_app TEST
    SOURCES
    termwise_eval_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_termwise_eval_test_app COMMAND searchlib_termwise_eval_test_app)
