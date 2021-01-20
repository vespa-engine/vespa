# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_mixed_inner_product_function_test_app TEST
    SOURCES
    mixed_inner_product_function_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_mixed_inner_product_function_test_app COMMAND eval_mixed_inner_product_function_test_app)
