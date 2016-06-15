# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_searchlib_index OBJECT
    SOURCES
    dictionaryfile.cpp
    docbuilder.cpp
    docidandfeatures.cpp
    doctypebuilder.cpp
    dummyfileheadercontext.cpp
    indexbuilder.cpp
    olddictionaryfile.cpp
    postinglisthandle.cpp
    postinglistcounts.cpp
    postinglistcountfile.cpp
    postinglistfile.cpp
    postinglistparams.cpp
    schemautil.cpp
    DEPENDS
)
