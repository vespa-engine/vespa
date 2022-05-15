# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    fnet
    vespalog
    vespalib
    searchlib
    searchcommon
    metrics
    config_cloudconfig
    fastlib_fast
    configdefinitions
    document
    persistencetypes

    LIBS
    src/vespa/searchcorespi
    src/vespa/searchcorespi/flush
    src/vespa/searchcorespi/index
)
