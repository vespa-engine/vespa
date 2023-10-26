# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_tstdst_app TEST
    SOURCES
    tstdst.cpp
    DEPENDS
)
vespa_add_executable(slobrok_rpc_info_app
    SOURCES
    rpc_info.cpp
    DEPENDS
)
vespa_add_test(NAME slobrok_tstdst_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/startsome.sh
               DEPENDS slobrok_tstdst_app slobrok_rpc_info_app slobrok_sbcmd_app slobrok_app)
