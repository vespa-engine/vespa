# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_vespa-dump-feed_app
    SOURCES
    vespa-dump-feed.cpp
    DEPENDS
)
vespa_add_target_system_dependency(searchcore_vespa-dump-feed_app boost boost_system${BOOST_LIB_SUFFIX})
vespa_add_target_system_dependency(searchcore_vespa-dump-feed_app boost boost_filesystem${BOOST_LIB_SUFFIX})
