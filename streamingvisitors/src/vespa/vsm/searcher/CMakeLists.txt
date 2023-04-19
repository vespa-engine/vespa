# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if(CMAKE_SYSTEM_PROCESSOR STREQUAL "x86_64")
  set(SSE2_FILES "fold.cpp")
else()
  unset(SSE2_FILES)
endif()

vespa_add_library(vsm_vsmsearcher OBJECT
    SOURCES
    boolfieldsearcher.cpp
    fieldsearcher.cpp
    floatfieldsearcher.cpp
    ${SSE2_FILES}
    futf8strchrfieldsearcher.cpp
    geo_pos_field_searcher.cpp
    intfieldsearcher.cpp
    nearest_neighbor_field_searcher.cpp
    strchrfieldsearcher.cpp
    utf8flexiblestringfieldsearcher.cpp
    utf8strchrfieldsearcher.cpp
    utf8stringfieldsearcherbase.cpp
    utf8substringsearcher.cpp
    utf8substringsnippetmodifier.cpp
    utf8suffixstringfieldsearcher.cpp
    utf8exactstringfieldsearcher.cpp
    DEPENDS
    vsm_vconfig
)
