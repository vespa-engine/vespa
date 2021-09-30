# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_util OBJECT
    SOURCES
    bytebuffer.cpp
    feed_reject_helper.cpp
    printable.cpp
    serializableexceptions.cpp
    stringutil.cpp
    DEPENDS
)
