# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_flushengine STATIC
    SOURCES
    cachedflushtarget.cpp
    flush_all_strategy.cpp
    flushcontext.cpp
    flushengine.cpp
    flush_engine_explorer.cpp
    flush_target_candidates.cpp
    flushtargetproxy.cpp
    flushtask.cpp
    prepare_restart_flush_strategy.cpp
    threadedflushtarget.cpp
    tls_stats_factory.cpp
    tls_stats_map.cpp
    DEPENDS
)
