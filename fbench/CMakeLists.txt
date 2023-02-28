# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalib

    APPS
    src/fbench
    src/filterfile
    src/geturl
    src/splitfile
    src/util
    src/httpclient

    TESTS
    src/test
    src/test/authority
)
