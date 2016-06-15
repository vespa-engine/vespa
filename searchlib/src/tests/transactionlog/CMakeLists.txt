# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_translogserver_test_app
    SOURCES
    translogserver_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_translogserver_test_app COMMAND searchlib_translogserver_test_app)
vespa_add_executable(searchlib_translogclient_test_app
    SOURCES
    translogclient_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_translogclient_test_app COMMAND sh translogclient_test.sh)
