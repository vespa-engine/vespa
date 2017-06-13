# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(memfilepersistence_mapper OBJECT
    SOURCES
    buffer.cpp
    memfilemapper.cpp
    serializationmetrics.cpp
    memfile_v1_serializer.cpp
    memfile_v1_verifier.cpp
    locationreadplanner.cpp
    simplememfileiobuffer.cpp
    fileinfo.cpp
    locationreadplanner.cpp
    bufferedfilewriter.cpp
    bucketdirectorymapper.cpp
    DEPENDS
)
