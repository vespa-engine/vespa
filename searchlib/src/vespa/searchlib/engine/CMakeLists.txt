# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_engine OBJECT
    SOURCES
    docsumapi.cpp
    docsumreply.cpp
    docsumrequest.cpp
    errorcodes.cpp
    monitorreply.cpp
    monitorrequest.cpp
    packetconverter.cpp
    propertiesmap.cpp
    request.cpp
    searchreply.cpp
    searchrequest.cpp
    source_description.cpp
    trace.cpp
    transport_metrics.cpp
    transportserver.cpp
    DEPENDS
)
