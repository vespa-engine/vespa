# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_query_streaming OBJECT
    SOURCES
    dot_product_term.cpp
    equiv_query_node.cpp
    fuzzy_term.cpp
    hit.cpp
    hit_iterator_pack.cpp
    in_term.cpp
    multi_term.cpp
    near_query_node.cpp
    nearest_neighbor_query_node.cpp
    onear_query_node.cpp
    phrase_query_node.cpp
    query.cpp
    querynode.cpp
    querynoderesultbase.cpp
    queryterm.cpp
    same_element_query_node.cpp
    wand_term.cpp
    weighted_set_term.cpp
    regexp_term.cpp
    DEPENDS
)
