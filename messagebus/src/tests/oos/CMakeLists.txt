# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_oos_test_app TEST
    SOURCES
    oos.cpp
    DEPENDS
    messagebus_messagebus-test
    messagebus
)
vespa_add_test(NAME messagebus_oos_test_app NO_VALGRIND COMMAND messagebus_oos_test_app)
