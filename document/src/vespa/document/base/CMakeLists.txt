# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_base OBJECT
    SOURCES
    documentcalculator.cpp
    documentid.cpp
    exceptions.cpp
    field.cpp
    fieldpath.cpp
    forcelink.cpp
    globalid.cpp
    idstring.cpp
    testdocman.cpp
    testdocrepo.cpp
    DEPENDS
)
