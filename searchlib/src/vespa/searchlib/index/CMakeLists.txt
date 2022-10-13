# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_searchlib_index OBJECT
    SOURCES
    dictionaryfile.cpp
    docidandfeatures.cpp
    dummyfileheadercontext.cpp
    indexbuilder.cpp
    postinglisthandle.cpp
    postinglistcounts.cpp
    postinglistcountfile.cpp
    postinglistfile.cpp
    postinglistparams.cpp
    schemautil.cpp
    schema_index_fields.cpp
    uri_field.cpp
    DEPENDS
)
