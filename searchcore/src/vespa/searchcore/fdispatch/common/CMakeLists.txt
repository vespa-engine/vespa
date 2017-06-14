# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_fdcommon STATIC
    SOURCES
    appcontext.cpp
    perftask.cpp
    queryperf.cpp
    rpc.cpp
    search.cpp
    timestat.cpp
    DEPENDS
)
