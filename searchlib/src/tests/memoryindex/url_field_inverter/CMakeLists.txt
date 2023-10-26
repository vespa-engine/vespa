# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_url_field_inverter_test_app TEST
    SOURCES
    url_field_inverter_test.cpp
    DEPENDS
    searchlib_test
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_url_field_inverter_test_app COMMAND searchlib_url_field_inverter_test_app)
