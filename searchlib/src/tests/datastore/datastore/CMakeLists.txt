# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_datastore_test_app TEST
    SOURCES
    datastore_test.cpp
    DEPENDS
    searchlib
    gtest
)
vespa_add_test(NAME searchlib_datastore_test_app COMMAND searchlib_datastore_test_app)
