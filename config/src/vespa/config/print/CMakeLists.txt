# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(config_print OBJECT
    SOURCES
    fileconfigformatter.cpp
    asciiconfigwriter.cpp
    fileconfigwriter.cpp
    ostreamconfigwriter.cpp
    jsonconfigformatter.cpp
    fileconfigsnapshotwriter.cpp
    fileconfigsnapshotreader.cpp
    asciiconfigsnapshotwriter.cpp
    asciiconfigsnapshotreader.cpp
    configdatabuffer.cpp
    DEPENDS
)
