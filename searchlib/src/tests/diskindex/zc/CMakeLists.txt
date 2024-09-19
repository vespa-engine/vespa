# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_zc_test_app TEST
    SOURCES
    zc_test.cpp
    DEPENDS
    searchlib_test
    vespa_searchlib
)
vespa_add_test(NAME searchlib_zc_test_app COMMAND searchlib_zc_test_app)
