# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_bitcompression OBJECT
    SOURCES
    compression.cpp
    countcompression.cpp
    pagedict4.cpp
    posocccompression.cpp
    DEPENDS
)
