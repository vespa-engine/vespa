# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_vespa-verify-logdatastore_app
    SOURCES
    verifylogdatastore.cpp
    OUTPUT_NAME vespa-verify-logdatastore
    INSTALL bin
    DEPENDS
    searchlib
)
vespa_add_executable(searchlib_vespa-documentstore-inspect_app
    SOURCES
    documentstoreinspect.cpp
    OUTPUT_NAME vespa-documentstore-inspect
    INSTALL bin
    DEPENDS
    searchlib
)
vespa_add_executable(searchlib_vespa-documentstore-benchmark_app
    SOURCES
    benchmarkdatastore.cpp
    OUTPUT_NAME vespa-documentstore-benchmark
    INSTALL bin
    DEPENDS
    searchlib
)
vespa_add_executable(searchlib_vespa-create-idx-from-dat_app
    SOURCES
    create-idx-from-dat.cpp
    OUTPUT_NAME vespa-create-idx-from-dat
    INSTALL bin
    DEPENDS
    searchlib
)
