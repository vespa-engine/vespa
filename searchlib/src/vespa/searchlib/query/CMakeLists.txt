# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_query OBJECT
    SOURCES
    queryterm.cpp
    querynode.cpp
    base.cpp
    query.cpp
    query_term_decoder.cpp
    querynoderesultbase.cpp
    DEPENDS
)
