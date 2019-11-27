# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_query OBJECT
    SOURCES
    query_term_simple.cpp
    queryterm.cpp
    querynode.cpp
    query.cpp
    query_term_decoder.cpp
    querynoderesultbase.cpp
    DEPENDS
)
