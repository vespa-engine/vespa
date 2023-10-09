# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(messagebus_routing OBJECT
    SOURCES
    errordirective.cpp
    hop.cpp
    hopblueprint.cpp
    hopspec.cpp
    policydirective.cpp
    resender.cpp
    retrytransienterrorspolicy.cpp
    route.cpp
    routedirective.cpp
    routeparser.cpp
    routespec.cpp
    routingcontext.cpp
    routingnode.cpp
    routingnodeiterator.cpp
    routingspec.cpp
    routingtable.cpp
    routingtablespec.cpp
    tcpdirective.cpp
    verbatimdirective.cpp
    DEPENDS
)
