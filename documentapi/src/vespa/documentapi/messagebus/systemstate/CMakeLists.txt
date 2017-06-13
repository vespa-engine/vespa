# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(documentapi_documentapisystemstate OBJECT
    SOURCES
    nodestate.cpp
    systemstate.cpp
    systemstatehandle.cpp
    urlencoder.cpp
    DEPENDS
)
