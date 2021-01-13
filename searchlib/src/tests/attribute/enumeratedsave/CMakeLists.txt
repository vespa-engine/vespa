# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_enumeratedsave_test_app TEST
    SOURCES
    enumeratedsave_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_enumeratedsave_test_app COMMAND searchlib_enumeratedsave_test_app COST 100)
