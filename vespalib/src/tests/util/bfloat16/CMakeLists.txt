# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
if(EXISTS /opt/vespa-deps/include/onnxruntime/onnxruntime_cxx_api.h)
vespa_add_executable(vespalib_bfloat16_test_app TEST
    SOURCES
    bfloat16_test.cpp
    DEPENDS
    vespalib
    onnxruntime
    GTest::GTest
)
vespa_add_test(NAME vespalib_bfloat16_test_app COMMAND vespalib_bfloat16_test_app)
endif()
