# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fastlib_net OBJECT
    SOURCES
    httpserver.cpp
    url.cpp
    socket.cpp
    httpheaderparser.cpp
    httpchunkedinputstream.cpp
    httpchunkedoutputstream.cpp
    DEPENDS
)
