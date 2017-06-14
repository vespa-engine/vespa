# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    staging_vespalib
    config_cloudconfig

    LIBS
    src/vespa/metrics

    TEST_EXTERNAL_DEPENDS
    cppunit

    TESTS
    src/tests
)
