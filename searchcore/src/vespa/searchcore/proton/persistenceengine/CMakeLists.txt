# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_persistenceengine STATIC
    SOURCES
    document_iterator.cpp
    i_document_retriever.cpp
    persistenceengine.cpp
    transport_latch.cpp
    DEPENDS
)
