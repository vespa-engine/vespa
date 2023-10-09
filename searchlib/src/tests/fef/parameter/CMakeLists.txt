# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_parameter_test_app TEST
    SOURCES
    parameter_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_parameter_test_app NO_VALGRIND COMMAND searchlib_parameter_test_app)
