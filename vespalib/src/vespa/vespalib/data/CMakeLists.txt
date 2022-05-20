# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_data OBJECT
    SOURCES
    databuffer.cpp
    fileheader.cpp
    input.cpp
    input_reader.cpp
    lz4_input_decoder.cpp
    lz4_output_encoder.cpp
    memory.cpp
    memory_input.cpp
    memorydatastore.cpp
    output.cpp
    output_writer.cpp
    simple_buffer.cpp
    smart_buffer.cpp
    writable_memory.cpp
    DEPENDS
)
