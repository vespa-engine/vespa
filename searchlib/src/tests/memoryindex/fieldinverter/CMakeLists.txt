# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_fieldinverter_test_app TEST
    SOURCES
    fieldinverter_test.cpp
    DEPENDS
    searchlib_test
    searchlib
)
vespa_add_test(NAME searchlib_fieldinverter_test_app COMMAND searchlib_fieldinverter_test_app)
