# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalib

    TESTS
    src/tests/dirconfig

    LIBS
    src/vespa/vdstestlib
    src/vespa/vdstestlib/config
)
