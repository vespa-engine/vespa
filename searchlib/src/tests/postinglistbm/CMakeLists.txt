# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_posting_list_test_app TEST
    SOURCES
    posting_list_test.cpp
    DEPENDS
    searchlib_test
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_posting_list_test_app NO_VALGRIND COMMAND searchlib_posting_list_test_app)

vespa_add_executable(searchlib_postinglistbm_app TEST
    SOURCES
    postinglistbm.cpp
    stress_runner.cpp
    DEPENDS
    searchlib_test
    searchlib
)
