# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_function_speed_test_app TEST
    SOURCES
    function_speed_test.cpp
    DEPENDS
    vespalib
    vespalib_vespalib_eval_llvm
)
vespa_add_test(NAME vespalib_function_speed_test_app NO_VALGRIND COMMAND vespalib_function_speed_test_app)
