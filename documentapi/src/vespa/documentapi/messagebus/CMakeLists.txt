# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(documentapi_documentapimessagebus OBJECT
    SOURCES
    documentprotocol.cpp
    replymerger.cpp
    routablefactories41.cpp
    routablefactories42.cpp
    routablefactories50.cpp
    routablefactories51.cpp
    routablefactories52.cpp
    routablerepository.cpp
    routingpolicyfactories.cpp
    routingpolicyrepository.cpp
    DEPENDS
    documentapi_documentapipolicies
)
