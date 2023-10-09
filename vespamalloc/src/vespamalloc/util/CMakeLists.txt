# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespamalloc_util OBJECT
    SOURCES
    callstack.cpp
    traceutil.cpp
    osmem.cpp
    stream.cpp
    DEPENDS
)
