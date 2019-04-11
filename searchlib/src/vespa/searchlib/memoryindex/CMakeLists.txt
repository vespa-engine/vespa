# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_memoryindex OBJECT
    SOURCES
    compact_document_words_store.cpp
    document_inverter.cpp
    featurestore.cpp
    field_index.cpp
    field_index_collection.cpp
    field_index_remover.cpp
    fieldinverter.cpp
    memoryindex.cpp
    ordered_field_index_inserter.cpp
    postingiterator.cpp
    urlfieldinverter.cpp
    wordstore.cpp
    DEPENDS
)
