# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_repo OBJECT
    SOURCES
    configbuilder.cpp
    documenttyperepo.cpp
    document_type_repo_factory.cpp
    fixedtyperepo.cpp
    DEPENDS
    AFTER
    document_documentconfig
)
