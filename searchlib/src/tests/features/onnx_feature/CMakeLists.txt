# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_onnx_feature_test_app TEST
    SOURCES
    onnx_feature_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_onnx_feature_test_app COMMAND searchlib_onnx_feature_test_app)
