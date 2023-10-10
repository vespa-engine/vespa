# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_test_dumpcodes_app TEST
    SOURCES
    dumpcodes.cpp
    DEPENDS
    messagebus
)
vespa_add_test(NAME messagebus_test_dumpcodes_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/errorcodes_test.sh
   DEPENDS messagebus_test_dumpcodes_app)
