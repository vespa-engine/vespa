# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_true_test_app TEST
    SOURCES
    true_test.cpp
    DEPENDS
)
vespa_add_test(NAME searchlib_true_test_app COMMAND searchlib_true_test_app)
