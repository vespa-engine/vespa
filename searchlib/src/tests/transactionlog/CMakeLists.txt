# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_translogclient_test_app TEST
    SOURCES
    translogclient_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_translogclient_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/translogclient_test.sh
               DEPENDS searchlib_translogclient_test_app COST 100)

vespa_add_executable(searchlib_translog_chunks_test_app TEST
    SOURCES
    chunks_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_translog_chunks_test_app COMMAND searchlib_translog_chunks_test_app)
