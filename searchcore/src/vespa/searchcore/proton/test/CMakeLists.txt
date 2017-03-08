# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_test STATIC
    SOURCES
    buckethandler.cpp
    clusterstatehandler.cpp
    documentdb_config_builder.cpp
    userdocumentsbuilder.cpp
    DEPENDS
    searchcore_fconfig
)
