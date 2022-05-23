# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastlib_unicodeutiltest_app TEST
    SOURCES
    unicodeutiltest.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME fastlib_unicodeutiltest_app NO_VALGRIND COMMAND fastlib_unicodeutiltest_app)
vespa_add_executable(fastlib_wordfolderstest_app TEST
    SOURCES
    wordfolderstest.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME fastlib_wordfolderstest_app NO_VALGRIND COMMAND fastlib_wordfolderstest_app)
