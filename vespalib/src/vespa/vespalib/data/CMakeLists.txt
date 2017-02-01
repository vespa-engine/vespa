# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_data OBJECT
    SOURCES
    input.cpp
    input_reader.cpp
    memory.cpp
    memorydatastore.cpp
    output.cpp
    output_writer.cpp
    simple_buffer.cpp
    writable_memory.cpp
    DEPENDS
)
