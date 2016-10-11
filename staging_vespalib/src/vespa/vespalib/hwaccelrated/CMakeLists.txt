# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib_vespalib_hwaccelrated OBJECT
    SOURCES
    iaccelrated.cpp
    generic.cpp
    sse2.cpp
    avx.cpp
    avx2.cpp
    DEPENDS
)
set_source_files_properties(avx.cpp PROPERTIES COMPILE_FLAGS -march=sandybridge)
set_source_files_properties(avx2.cpp PROPERTIES COMPILE_FLAGS -march=broadwell)
