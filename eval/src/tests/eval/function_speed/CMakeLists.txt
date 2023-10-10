# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_function_speed_test_app TEST
    SOURCES
    function_speed_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_function_speed_test_app NO_VALGRIND COMMAND eval_function_speed_test_app)
