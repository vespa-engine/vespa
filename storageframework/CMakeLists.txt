# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    metrics

    LIBS
    src/vespa/storageframework
    src/vespa/storageframework/defaultimplementation
    src/vespa/storageframework/defaultimplementation/clock
    src/vespa/storageframework/defaultimplementation/component
    src/vespa/storageframework/defaultimplementation/thread
    src/vespa/storageframework/generic
    src/vespa/storageframework/generic/clock
    src/vespa/storageframework/generic/component
    src/vespa/storageframework/generic/metric
    src/vespa/storageframework/generic/status
    src/vespa/storageframework/generic/thread

    TEST_EXTERNAL_DEPENDS
    vdstestlib

    TESTS
    src/tests
    src/tests/clock
    src/tests/thread
)
