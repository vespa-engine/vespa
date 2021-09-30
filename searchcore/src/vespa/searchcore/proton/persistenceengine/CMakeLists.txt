# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_persistenceengine STATIC
    SOURCES
    document_iterator.cpp
    commit_and_wait_document_retriever.cpp
    i_document_retriever.cpp
    persistenceengine.cpp
    persistence_handler_map.cpp
    resource_usage_tracker.cpp
    transport_latch.cpp
    DEPENDS
)
