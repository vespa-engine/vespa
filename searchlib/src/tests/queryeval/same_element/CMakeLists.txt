# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_same_element_test_app TEST
    SOURCES
    same_element_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_same_element_test_app COMMAND searchlib_same_element_test_app)
