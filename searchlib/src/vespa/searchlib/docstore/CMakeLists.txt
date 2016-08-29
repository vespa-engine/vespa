# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_docstore OBJECT
    SOURCES
    bytecomplens.cpp
    chunk.cpp
    chunkformat.cpp
    chunkformats.cpp
    compacter.cpp
    data_store_file_chunk_id.cpp
    documentstore.cpp
    document_store_visitor_progress.cpp
    filechunk.cpp
    idatastore.cpp
    idocumentstore.cpp
    logdatastore.cpp
    logdocumentstore.cpp
    storebybucket.cpp
    visitcache.cpp
    writeablefilechunk.cpp
    DEPENDS
)
