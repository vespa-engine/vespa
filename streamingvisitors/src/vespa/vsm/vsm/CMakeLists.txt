# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vsm_vsmbase OBJECT
    SOURCES
    docsumfieldspec.cpp
    docsumfilter.cpp
    docsum_field_writer_factory.cpp
    fieldsearchspec.cpp
    flattendocsumwriter.cpp
    query_term_filter_factory.cpp
    snippetmodifier.cpp
    vsm-adapter.cpp
    DEPENDS
    vsm_vconfig
)
