# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
find_package(GTest REQUIRED)
vespa_add_executable(searchlib_equiv_test_app TEST
    SOURCES
    equiv_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_equiv_test_app COMMAND searchlib_equiv_test_app)
