# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_statefile_test_app TEST
    SOURCES
    statefile_test.cpp
    DEPENDS
    searchlib_test
    searchlib
)
vespa_add_test(NAME searchlib_statefile_test_app COMMAND searchlib_statefile_test_app)
