# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_vector_from_doubles_function_test_app TEST
    SOURCES
    vector_from_doubles_function_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_vector_from_doubles_function_test_app COMMAND eval_vector_from_doubles_function_test_app)
