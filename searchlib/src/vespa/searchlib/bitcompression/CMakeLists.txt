# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_bitcompression OBJECT
    SOURCES
    compression.cpp
    countcompression.cpp
    pagedict4.cpp
    posocccompression.cpp
    posocc_fields_params.cpp
    posocc_field_params.cpp
    DEPENDS
)
