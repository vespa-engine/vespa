# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(documentapi_messages_test_app TEST
    SOURCES
    error_codes_test.cpp
    message_fixture.cpp
    messages60test.cpp
    messages80test.cpp
    messages_app.cpp
    DEPENDS
    documentapi
    GTest::GTest
)
vespa_add_test(NAME documentapi_messages_test_app COMMAND documentapi_messages_test_app)
