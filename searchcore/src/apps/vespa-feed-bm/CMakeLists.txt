# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_vespa_feed_bm_app
    SOURCES
    vespa_feed_bm.cpp
    OUTPUT_NAME vespa-feed-bm
    DEPENDS
    searchcore_bmcluster
)
