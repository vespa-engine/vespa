# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_messageordering_test_app TEST
    SOURCES
    messageordering.cpp
    DEPENDS
    messagebus_messagebus-test
    messagebus
)
vespa_add_test(
    NAME messagebus_messageordering_test_app
    COMMAND messagebus_messageordering_test_app
)
