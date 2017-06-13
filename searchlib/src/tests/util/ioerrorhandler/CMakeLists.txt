# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_ioerrorhandler_test_app TEST
    SOURCES
    ioerrorhandler_test.cpp
    DEPENDS
    searchlib_test
    searchlib
    dl
)
vespa_add_test(NAME searchlib_ioerrorhandler_test_app COMMAND searchlib_ioerrorhandler_test_app)
