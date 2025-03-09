# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_perdocexpr_test_app TEST
    SOURCES
    perdocexpr_test.cpp
    DEPENDS
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_perdocexpr_test_app COMMAND searchlib_perdocexpr_test_app)
vespa_add_executable(searchlib_attr_test_app TEST
    SOURCES
    attr_test.cpp
    DEPENDS
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_attr_test_app COMMAND searchlib_attr_test_app)
