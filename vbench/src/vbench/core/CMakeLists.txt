# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vbench_core OBJECT
    SOURCES
    closeable.cpp
    dispatcher.cpp
    handler.cpp
    handler_thread.cpp
    input_file_reader.cpp
    line_reader.cpp
    provider.cpp
    socket.cpp
    stream.cpp
    string.cpp
    taint.cpp
    taintable.cpp
    time_queue.cpp
    timer.cpp
    DEPENDS
)
