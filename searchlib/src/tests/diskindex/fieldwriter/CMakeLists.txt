# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_fieldwriter_test_app TEST
    SOURCES
    fieldwriter_test.cpp
    DEPENDS
    searchlib_test
    searchlib
)
vespa_add_test(NAME searchlib_fieldwriter_test_app COMMAND searchlib_fieldwriter_test_app COST 200)
