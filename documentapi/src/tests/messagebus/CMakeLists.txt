# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(documentapi_messagebus_test_app TEST
    SOURCES
    messagebus_test.cpp
    DEPENDS
    documentapi
)
vespa_add_test(NAME documentapi_messagebus_test_app COMMAND documentapi_messagebus_test_app)
