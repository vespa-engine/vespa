# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vdslib_state OBJECT
    SOURCES
    nodetype.cpp
    node.cpp
    state.cpp
    diskstate.cpp
    nodestate.cpp
    clusterstate.cpp
    cluster_state_bundle.cpp
    DEPENDS
)
