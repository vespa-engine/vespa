# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_query_tree OBJECT
    SOURCES
    const_bool_nodes.cpp
    intermediate.cpp
    intermediatenodes.cpp
    location.cpp
    querybuilder.cpp
    range.cpp
    simplequery.cpp
    stackdumpcreator.cpp
    stackdumpquerycreator.cpp
    term.cpp
    termnodes.cpp
    DEPENDS
)
