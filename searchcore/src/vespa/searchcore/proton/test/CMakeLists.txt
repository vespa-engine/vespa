# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_test STATIC
    SOURCES
    bucketfactory.cpp
    buckethandler.cpp
    clusterstatehandler.cpp
    documentdb_config_builder.cpp
    dummy_feed_view.cpp
    userdocumentsbuilder.cpp
    DEPENDS
    searchcore_fconfig
)
