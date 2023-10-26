# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(logd_empty_forwarder_test_app TEST
    SOURCES
    empty_forwarder_test.cpp
    DEPENDS
    logd
    GTest::GTest
)
vespa_add_test(NAME logd_empty_forwarder_test_app COMMAND logd_empty_forwarder_test_app)
