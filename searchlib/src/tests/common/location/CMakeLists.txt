# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_location_test_app TEST
    SOURCES
    location_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_location_test_app COMMAND searchlib_location_test_app)

vespa_add_executable(searchlib_geo_location_test_app TEST
    SOURCES
    geo_location_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_geo_location_test_app COMMAND searchlib_geo_location_test_app)
