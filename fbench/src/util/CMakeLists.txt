# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fbench_util STATIC
    SOURCES
    authority.cpp
    clientstatus.cpp
    filereader.cpp
    timer.cpp
    DEPENDS
)
