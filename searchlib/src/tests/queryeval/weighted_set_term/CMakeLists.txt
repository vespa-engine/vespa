# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_weighted_set_term_test_app TEST
    SOURCES
    weighted_set_term_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_weighted_set_term_test_app COMMAND searchlib_weighted_set_term_test_app)
