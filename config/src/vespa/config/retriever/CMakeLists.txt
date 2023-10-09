# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(config_retriever OBJECT
    SOURCES
    configretriever.cpp
    configsnapshot.cpp
    genericconfigsubscriber.cpp
    fixedconfigsubscriber.cpp
    configkeyset.cpp
    simpleconfigretriever.cpp
    simpleconfigurer.cpp
    DEPENDS
)
