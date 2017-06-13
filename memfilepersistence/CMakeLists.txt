# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespadefaults
    fastos
    vespalog
    vespalib
    document
    config_cloudconfig
    configdefinitions
    vdslib
    persistence
    storageframework

    LIBS
    src/vespa/memfilepersistence
    src/vespa/memfilepersistence/common
    src/vespa/memfilepersistence/device
    src/vespa/memfilepersistence/init
    src/vespa/memfilepersistence/mapper
    src/vespa/memfilepersistence/memfile
    src/vespa/memfilepersistence/spi
    src/vespa/memfilepersistence/tools

    TEST_DEPENDS
    persistence_persistence_conformancetest
    vdstestlib

    TESTS
    src/tests
    src/tests/conformance
    src/tests/device
    src/tests/init
    src/tests/helper
    src/tests/spi
    src/tests/tools
)
