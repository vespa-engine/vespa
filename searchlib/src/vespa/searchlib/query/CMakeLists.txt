# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_query OBJECT
    SOURCES
    query_term_simple.cpp
    query_term_ucs4.cpp
    query_term_decoder.cpp
    DEPENDS
)
