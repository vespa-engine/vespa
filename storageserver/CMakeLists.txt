# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    storage
    streamingvisitors_searchvisitor

    APPS
    src/apps/storaged
    src/vespa/storageserver/app

    TEST_DEPENDS
    messagebus_messagebus-test

    TESTS
    src/tests
)
