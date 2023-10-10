# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_docstore OBJECT
    SOURCES
    chunk.cpp
    chunkformat.cpp
    chunkformats.cpp
    compacter.cpp
    data_store_file_chunk_id.cpp
    document_store_visitor_progress.cpp
    documentstore.cpp
    filechunk.cpp
    idatastore.cpp
    idocumentstore.cpp
    lid_info.cpp
    logdatastore.cpp
    logdocumentstore.cpp
    randreaders.cpp
    storebybucket.cpp
    summaryexceptions.cpp
    value.cpp
    visitcache.cpp
    writeablefilechunk.cpp
    DEPENDS
)
