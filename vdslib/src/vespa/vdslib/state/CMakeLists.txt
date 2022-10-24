# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vdslib_state OBJECT
    SOURCES
    globals.cpp
    nodetype.cpp
    node.cpp
    state.cpp
    nodestate.cpp
    clusterstate.cpp
    cluster_state_bundle.cpp
    DEPENDS
)
