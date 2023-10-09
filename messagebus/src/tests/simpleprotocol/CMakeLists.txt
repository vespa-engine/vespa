# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_simpleprotocol_test_app TEST
    SOURCES
    simpleprotocol.cpp
    DEPENDS
    messagebus_messagebus-test
    messagebus
)
vespa_add_test(NAME messagebus_simpleprotocol_test_app COMMAND messagebus_simpleprotocol_test_app)
