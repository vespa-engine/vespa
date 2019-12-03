# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib_vespalib_util OBJECT
    SOURCES
    bits.cpp
    clock.cpp
    crc.cpp
    doom.cpp
    growablebytebuffer.cpp
    jsonexception.cpp
    jsonstream.cpp
    jsonwriter.cpp
    librarypool.cpp
    process_memory_stats.cpp
    programoptions.cpp
    programoptions_testutils.cpp
    document_runnable.cpp
    rusage.cpp
    shutdownguard.cpp
    scheduledexecutor.cpp
    xmlserializable.cpp
    xmlstream.cpp
    DEPENDS
)
