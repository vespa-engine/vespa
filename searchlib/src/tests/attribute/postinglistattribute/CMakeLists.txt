# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_postinglistattribute_test_app TEST
    SOURCES
    postinglistattribute_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_postinglistattribute_test_app COMMAND searchlib_postinglistattribute_test_app)
