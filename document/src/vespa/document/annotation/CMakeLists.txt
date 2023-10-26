# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_annotation OBJECT
    SOURCES
    alternatespanlist.cpp
    annotation.cpp
    span.cpp
    spanlist.cpp
    spannode.cpp
    spantree.cpp
    DEPENDS
)
