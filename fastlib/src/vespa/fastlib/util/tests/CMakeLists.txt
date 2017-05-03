# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastlib_base64test_app TEST
    SOURCES
    base64test.cpp
    DEPENDS
    fastlib_util
    fastlib_fast_testsuite
)
vespa_add_test(NAME fastlib_base64test_app NO_VALGRIND COMMAND fastlib_base64test_app fastlib_base64test_app)
vespa_add_executable(fastlib_bagtest_app TEST
    SOURCES
    bagtest.cpp
    DEPENDS
    fastlib_util
    fastlib_fast_testsuite
)
vespa_add_test(NAME fastlib_bagtest_app NO_VALGRIND COMMAND fastlib_bagtest_app)
vespa_add_executable(fastlib_wildcard_match_test_app TEST
    SOURCES
    wildcard_match_test.cpp
    DEPENDS
    fastlib_fast_testsuite
)
vespa_add_test(NAME fastlib_wildcard_match_test_app NO_VALGRIND COMMAND fastlib_wildcard_match_test_app)
