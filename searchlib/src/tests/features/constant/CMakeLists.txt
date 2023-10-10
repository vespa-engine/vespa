# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_constant_test_app TEST
    SOURCES
    constant_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_constant_test_app COMMAND searchlib_constant_test_app)
