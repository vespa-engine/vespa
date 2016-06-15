# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vbench_core OBJECT
    SOURCES
    buffered_output.cpp
    byte_input.cpp
    closeable.cpp
    dispatcher.cpp
    handler.cpp
    handler_thread.cpp
    input.cpp
    input_file_reader.cpp
    line_reader.cpp
    mapped_file_input.cpp
    memory.cpp
    output.cpp
    provider.cpp
    simple_buffer.cpp
    socket.cpp
    stream.cpp
    string.cpp
    taint.cpp
    taintable.cpp
    time_queue.cpp
    timer.cpp
    writable_memory.cpp
    DEPENDS
)
