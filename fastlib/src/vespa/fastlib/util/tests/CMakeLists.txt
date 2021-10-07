# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastlib_wildcard_match_test_app TEST
    SOURCES
    wildcard_match_test.cpp
    DEPENDS
    fastlib_fast_testsuite
)
vespa_add_test(NAME fastlib_wildcard_match_test_app NO_VALGRIND COMMAND fastlib_wildcard_match_test_app)
