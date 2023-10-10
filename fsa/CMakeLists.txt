# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    LIBS
    src/vespa/fsa
    src/vespa/fsamanagers

    APPS
    src/apps/fsadump
    src/apps/fsainfo
    src/apps/makefsa

    TESTS
    src/alltest
)
