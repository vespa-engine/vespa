# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_rcuvector_test_app TEST
    SOURCES
    rcuvector_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_rcuvector_test_app COMMAND searchlib_rcuvector_test_app)
