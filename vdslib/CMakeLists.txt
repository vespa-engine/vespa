# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    config_cloudconfig
    configdefinitions
    document

    LIBS
    src/vespa/vdslib
    src/vespa/vdslib/container
    src/vespa/vdslib/distribution
    src/vespa/vdslib/state

    TEST_DEPENDS
    vdstestlib

    TESTS
    src/tests
    src/tests/container
    src/tests/distribution
    src/tests/state
)
