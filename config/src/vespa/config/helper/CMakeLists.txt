# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(config_helper OBJECT
    SOURCES
    configfetcher.cpp
    legacysubscriber.cpp
    legacy.cpp
    configpoller.cpp
    DEPENDS
)
