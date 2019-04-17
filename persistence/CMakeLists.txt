# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    staging_vespalib
    fnet
    document
    persistencetypes
    config_cloudconfig
    vdslib
    metrics
    configdefinitions

    LIBS
    src/vespa/persistence
    src/vespa/persistence/conformancetest
    src/vespa/persistence/dummyimpl
    src/vespa/persistence/spi

    TEST_DEPENDS
    gtest

    TESTS
    src/tests
    src/tests/dummyimpl
    src/tests/spi
)
