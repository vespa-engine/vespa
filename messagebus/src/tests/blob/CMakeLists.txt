# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_blob_test_app TEST
    SOURCES
    blob.cpp
    DEPENDS
    messagebus
    messagebus_messagebus-test
)
vespa_add_test(NAME messagebus_blob_test_app COMMAND messagebus_blob_test_app)
