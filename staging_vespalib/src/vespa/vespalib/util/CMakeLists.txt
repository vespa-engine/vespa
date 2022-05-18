# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib_vespalib_util OBJECT
    SOURCES
    bits.cpp
    clock.cpp
    crc.cpp
    document_runnable.cpp
    doom.cpp
    growablebytebuffer.cpp
    jsonexception.cpp
    jsonstream.cpp
    malloc_mmap_guard.cpp
    process_memory_stats.cpp
    programoptions.cpp
    programoptions_testutils.cpp
    rusage.cpp
    shutdownguard.cpp
    testclock.cpp
    xmlserializable.cpp
    xmlstream.cpp
    DEPENDS
)
