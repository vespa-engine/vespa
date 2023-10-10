# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_expgolomb_test_app TEST
    SOURCES
    expgolomb_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_expgolomb_test_app NO_VALGRIND COMMAND searchlib_expgolomb_test_app)
