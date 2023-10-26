# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(slobrok
    SOURCES
    backoff.cpp
    sblist.cpp
    cfg.cpp
    sbmirror.cpp
    sbregister.cpp
    INSTALL lib64
    DEPENDS
)
