# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcommon_schema_test_app TEST
    SOURCES
    schema_test.cpp
    DEPENDS
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchcommon_schema_test_app NO_VALGRIND COMMAND searchcommon_schema_test_app)
