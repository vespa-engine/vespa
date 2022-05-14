# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    fastlib_fast
    config_cloudconfig
    vespalib
    document
    searchlib
    searchsummary

    TESTS
    src/tests/charbuffer
    src/tests/docsum
    src/tests/document
    src/tests/searcher
    src/tests/textutil

    LIBS
    src/vespa/vsm
    src/vespa/vsm/common
    src/vespa/vsm/config
    src/vespa/vsm/searcher
    src/vespa/vsm/vsm
)
