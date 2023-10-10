# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_trace OBJECT
    SOURCES
    trace.cpp
    tracenode.cpp
    slime_trace_serializer.cpp
    slime_trace_deserializer.cpp
    DEPENDS
)
