# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(eval_value_codec_test_app TEST
    SOURCES
    value_codec_test.cpp
    DEPENDS
    vespaeval
    GTest::GTest
)
vespa_add_test(NAME eval_value_codec_test_app COMMAND eval_value_codec_test_app)
