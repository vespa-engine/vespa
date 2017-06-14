# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fastlib_io
    SOURCES
    fileinputstream.cpp
    fileoutputstream.cpp
    bufferedfile.cpp
    bufferedinputstream.cpp
    bufferedoutputstream.cpp
    INSTALL lib64
    DEPENDS
)
