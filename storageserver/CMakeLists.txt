# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespa_storage
    vespa_streamingvisitors

    APPS
    src/apps/storaged
    src/vespa/storageserver/app

    TEST_DEPENDS
    vespa_messagebus-test

    TESTS
    src/tests
)
