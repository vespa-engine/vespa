# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_searchlib_test_diskindex OBJECT
    SOURCES
    compressed_read_buffer.cpp
    compressed_write_buffer.cpp
    pagedict4_decoders.cpp
    pagedict4_encoders.cpp
    pagedict4_mem_seq_reader.cpp
    pagedict4_mem_rand_reader.cpp
    pagedict4_mem_writer.cpp
    threelevelcountbuffers.cpp
    testdiskindex.cpp
    DEPENDS
)
