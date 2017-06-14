# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_memoryindex OBJECT
    SOURCES
    compact_document_words_store.cpp
    dictionary.cpp
    documentinverter.cpp
    document_remover.cpp
    featurestore.cpp
    fieldinverter.cpp
    memoryfieldindex.cpp
    memoryindex.cpp
    ordereddocumentinserter.cpp
    postingiterator.cpp
    urlfieldinverter.cpp
    wordstore.cpp
    DEPENDS
)
