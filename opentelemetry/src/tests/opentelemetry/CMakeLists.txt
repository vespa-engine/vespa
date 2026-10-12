# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(opentelemetry_opentelemetry_test_app TEST
    SOURCES
    opentelemetry_test.cpp
    OUTPUT_NAME vespa-opentelemetry-test
    INSTALL bin
    DEPENDS
    vespa_opentelemetry
    GTest::gtest
)
vespa_add_test(NAME opentelemetry_opentelemetry_test_app COMMAND opentelemetry_opentelemetry_test_app)
