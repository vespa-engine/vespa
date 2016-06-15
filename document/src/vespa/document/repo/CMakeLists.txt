# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_repo OBJECT
    SOURCES
    configbuilder.cpp
    documenttyperepo.cpp
    fixedtyperepo.cpp
    DEPENDS
    AFTER
    document_documentconfig
)
