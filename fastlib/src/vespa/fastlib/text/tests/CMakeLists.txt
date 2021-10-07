# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastlib_unicodeutiltest_app TEST
    SOURCES
    unicodeutiltest.cpp
    DEPENDS
    fastlib_text
    fastlib_fast_testsuite
)
vespa_add_test(NAME fastlib_unicodeutiltest_app NO_VALGRIND COMMAND fastlib_unicodeutiltest_app)
vespa_add_executable(fastlib_latintokenizertest_app TEST
    SOURCES
    latintokenizertest.cpp
    DEPENDS
    fastlib_text
    fastlib_fast_testsuite
)
vespa_add_test(NAME fastlib_latintokenizertest_app NO_VALGRIND COMMAND fastlib_latintokenizertest_app)
vespa_add_executable(fastlib_wordfolderstest_app TEST
    SOURCES
    wordfolderstest.cpp
    DEPENDS
    fastlib_text
    fastlib_fast_testsuite
)
vespa_add_test(NAME fastlib_wordfolderstest_app NO_VALGRIND COMMAND fastlib_wordfolderstest_app)
