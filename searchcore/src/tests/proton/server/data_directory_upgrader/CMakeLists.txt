# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_data_directory_upgrader_test_app TEST
    SOURCES
    data_directory_upgrader_test.cpp
    DEPENDS
    searchcore_server
    searchcore_flushengine
)
vespa_add_test(NAME searchcore_data_directory_upgrader_test_app COMMAND searchcore_data_directory_upgrader_test_app)
