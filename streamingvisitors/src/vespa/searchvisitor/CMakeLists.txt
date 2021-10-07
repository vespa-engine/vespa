# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(streamingvisitors_searchvisitor
    SOURCES
    hitcollector.cpp
    indexenvironment.cpp
    matching_elements_filler.cpp
    queryenvironment.cpp
    querytermdata.cpp
    querywrapper.cpp
    rankmanager.cpp
    rankprocessor.cpp
    searchenvironment.cpp
    searchvisitor.cpp
    INSTALL lib64
    DEPENDS
    searchlib_searchlib_uca
)
