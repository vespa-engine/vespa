# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_hwaccelrated OBJECT
    SOURCES
    iaccelrated.cpp
    generic.cpp
    avx2.cpp
    avx512.cpp
    DEPENDS
)
set_source_files_properties(avx.cpp PROPERTIES COMPILE_FLAGS -march=sandybridge)
set_source_files_properties(avx2.cpp PROPERTIES COMPILE_FLAGS -march=haswell)
set_source_files_properties(avx512.cpp PROPERTIES COMPILE_FLAGS -march=skylake-avx512)
