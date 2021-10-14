# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vsm_vsmbase OBJECT
    SOURCES
    docsumfieldspec.cpp
    docsumfilter.cpp
    fieldsearchspec.cpp
    flattendocsumwriter.cpp
    slimefieldwriter.cpp
    snippetmodifier.cpp
    vsm-adapter.cpp
    docsumconfig.cpp
    DEPENDS
    vsm_vconfig
)
