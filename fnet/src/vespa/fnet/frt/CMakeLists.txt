# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fnet_frt OBJECT
    SOURCES
    error.cpp
    invoker.cpp
    packets.cpp
    reflection.cpp
    require_capabilities.cpp
    rpcrequest.cpp
    supervisor.cpp
    target.cpp
    values.cpp
    DEPENDS
)
