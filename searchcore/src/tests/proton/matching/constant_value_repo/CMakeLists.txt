# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_constant_value_repo_test_app TEST
    SOURCES
    constant_value_repo_test.cpp
    DEPENDS
    searchcore_matching
    GTest::gtest
)
vespa_add_test(NAME searchcore_constant_value_repo_test_app COMMAND searchcore_constant_value_repo_test_app)
