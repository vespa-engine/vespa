# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_slobrok_test_app TEST
    SOURCES
    slobrok.cpp
    DEPENDS
    messagebus_messagebus-test
    messagebus
)
vespa_add_test(NAME messagebus_slobrok_test_app COMMAND messagebus_slobrok_test_app)
