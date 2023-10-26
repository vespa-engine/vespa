# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(configutil_util STATIC
    SOURCES
    tags.cpp
    modelinspect.cpp
    configstatus.cpp
    DEPENDS
    configdefinitions
)
