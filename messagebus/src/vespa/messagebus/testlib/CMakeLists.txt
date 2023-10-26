# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(messagebus_messagebus-test
    SOURCES
    custompolicy.cpp
    receptor.cpp
    simplemessage.cpp
    simpleprotocol.cpp
    simplereply.cpp
    slobrok.cpp
    slobrokstate.cpp
    testserver.cpp
    INSTALL lib64
    DEPENDS
    messagebus
    slobrok_slobrokserver
)
