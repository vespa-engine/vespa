# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(staging_vespalib_vespalib_util OBJECT
    SOURCES
    adaptive_sequenced_executor.cpp
    bits.cpp
    clock.cpp
    crc.cpp
    doom.cpp
    foregroundtaskexecutor.cpp
    growablebytebuffer.cpp
    isequencedtaskexecutor.cpp
    jsonexception.cpp
    jsonstream.cpp
    jsonwriter.cpp
    process_memory_stats.cpp
    programoptions.cpp
    programoptions_testutils.cpp
    document_runnable.cpp
    rusage.cpp
    sequencedtaskexecutor.cpp
    sequencedtaskexecutorobserver.cpp
    shutdownguard.cpp
    scheduledexecutor.cpp
    singleexecutor.cpp
    xmlserializable.cpp
    xmlstream.cpp
    DEPENDS
)
