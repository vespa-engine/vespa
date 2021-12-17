# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_geo_gcd_test_app TEST
    SOURCES
    geo_gcd_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_geo_gcd_test_app COMMAND searchlib_geo_gcd_test_app)
