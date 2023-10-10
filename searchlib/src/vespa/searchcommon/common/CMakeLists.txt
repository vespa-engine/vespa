# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcommon_searchcommon_common OBJECT
    SOURCES
    datatype.cpp
    dictionary_config.cpp
    growstrategy.cpp
    schema.cpp
    schemaconfigurer.cpp
    DEPENDS
)

file(GLOB HEADERS *.h)
install(FILES ${HEADERS} DESTINATION include/vespa/searchcommon/common)
