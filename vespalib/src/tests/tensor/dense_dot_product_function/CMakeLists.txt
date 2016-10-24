# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_dense_dot_product_function_test_app TEST
    SOURCES
    dense_dot_product_function_test.cpp
    DEPENDS
    vespalib
    vespalib_vespalib_tensor
)
vespa_add_test(NAME vespalib_dense_dot_product_function_test_app COMMAND vespalib_dense_dot_product_function_test_app)
