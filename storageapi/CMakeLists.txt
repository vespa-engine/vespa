# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    fnet
    vespalog
    vespalib
    document
    messagebus
    vdslib
    documentapi

    LIBS
    src/vespa/storageapi
    src/vespa/storageapi/app
    src/vespa/storageapi/buckets
    src/vespa/storageapi/mbusprot
    src/vespa/storageapi/message
    src/vespa/storageapi/messageapi

    TESTS
    src/tests
    src/tests/buckets
    src/tests/mbusprot
    src/tests/messageapi
)
