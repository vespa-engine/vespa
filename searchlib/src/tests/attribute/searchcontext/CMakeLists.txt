# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_searchcontext_test_app TEST
    SOURCES
    searchcontext_test.cpp
    DEPENDS
    vespa_searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_searchcontext_test_app COMMAND searchlib_searchcontext_test_app COST 100)
