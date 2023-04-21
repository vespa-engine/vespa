# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageapi_message OBJECT
    SOURCES
    datagram.cpp
    persistence.cpp
    bucket.cpp
    visitor.cpp
    state.cpp
    bucketsplitting.cpp
    stat.cpp
    removelocation.cpp
    queryresult.cpp
    internal.cpp
    DEPENDS
)
