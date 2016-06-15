# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_fdispatch_program STATIC
    SOURCES
    fdispatch.cpp
    rpc.cpp
    engineadapter.cpp
    searchadapter.cpp
    docsumadapter.cpp
    DEPENDS
    searchcore_fconfig
)
