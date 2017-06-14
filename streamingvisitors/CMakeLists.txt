# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    fastlib_fast
    vespalog
    storage
    storageapi
    config_cloudconfig
    document
    vespalib
    vdslib
    vsm

    TEST_EXTERNAL_DEPENDS
    cppunit

    LIBS
    src/vespa/searchvisitor

    TESTS
    src/tests/hitcollector
    src/tests/querywrapper
    src/tests/searchvisitor
)
