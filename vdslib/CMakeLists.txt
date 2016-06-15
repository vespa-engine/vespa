# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    staging_vespalib
    config_cloudconfig
    configdefinitions
    document

    LIBS
    src/vespa/vdslib
    src/vespa/vdslib/container
    src/vespa/vdslib/distribution
    src/vespa/vdslib/state
    src/vespa/vdslib/thread

    TEST_DEPENDS
    vdstestlib
    cppunit

    TESTS
    src/tests
    src/tests/bucketdistribution
    src/tests/container
    src/tests/distribution
    src/tests/state
    src/tests/thread
)
