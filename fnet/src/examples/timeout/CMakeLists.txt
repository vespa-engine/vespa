# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_timeout_app
    SOURCES
    timeout.cpp
    DEPENDS
    fnet
)
