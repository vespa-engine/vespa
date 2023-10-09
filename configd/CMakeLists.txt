# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    APPS
    src/apps/sentinel
    src/apps/cmd
    src/apps/su

    TESTS
    src/tests/messages
    src/tests/configd
)
