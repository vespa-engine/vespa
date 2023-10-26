# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_fake_searchable_test_app TEST
    SOURCES
    fake_searchable_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_fake_searchable_test_app COMMAND searchlib_fake_searchable_test_app)
