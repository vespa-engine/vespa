# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_visibility_handler_test_app TEST
    SOURCES
    visibility_handler_test.cpp
    DEPENDS
    searchcore_server
    searchcore_test
)
vespa_add_test(NAME searchcore_visibility_handler_test_app COMMAND searchcore_visibility_handler_test_app)
