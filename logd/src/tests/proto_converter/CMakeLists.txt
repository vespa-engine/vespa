# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(logd_proto_converter_test_app TEST
    SOURCES
    proto_converter_test.cpp
    DEPENDS
    logd
    GTest::GTest
)
vespa_add_test(NAME logd_proto_converter_test_app COMMAND logd_proto_converter_test_app)
