# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    storage
    streamingvisitors

    APPS
    src/apps/storaged
    src/vespa/storageserver/app

    TEST_DEPENDS
    messagebus_messagebus-test

    TESTS
    src/tests
)
