# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_configvalidator_test_app
    SOURCES
    configvalidator_test.cpp
    DEPENDS
    searchcore_server
)
vespa_add_test(NAME searchcore_configvalidator_test_app COMMAND searchcore_configvalidator_test_app)
