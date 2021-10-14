# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    LIBS
    src/vespa/searchvisitor

    TESTS
    src/tests/hitcollector
    src/tests/matching_elements_filler
    src/tests/querywrapper
    src/tests/searchvisitor
)
