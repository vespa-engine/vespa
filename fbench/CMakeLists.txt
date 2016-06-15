# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos

    APPS
    src/fbench
    src/filterfile
    src/geturl
    src/splitfile
    src/util

    TESTS
    src/test
)
