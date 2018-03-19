# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageapi_message OBJECT
    SOURCES
    datagram.cpp
    persistence.cpp
    bucket.cpp
    visitor.cpp
    state.cpp
    searchresult.cpp
    bucketsplitting.cpp
    documentsummary.cpp
    stat.cpp
    removelocation.cpp
    queryresult.cpp
    batch.cpp
    internal.cpp
    DEPENDS
)
