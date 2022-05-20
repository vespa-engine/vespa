# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcommon_searchcommon_attribute OBJECT
    SOURCES
    attribute_utils.cpp
    basictype.cpp
    collectiontype.cpp
    config.cpp
    search_context_params.cpp
    status.cpp
    DEPENDS
)
