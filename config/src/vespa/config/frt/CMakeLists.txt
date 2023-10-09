# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(config_frt OBJECT
    SOURCES
    frtsource.cpp
    frtconnectionpool.cpp
    frtconnection.cpp
    frtconfigrequest.cpp
    frtconfigresponse.cpp
    frtsourcefactory.cpp
    frtconfigagent.cpp
    frtconfigrequestfactory.cpp
    protocol.cpp
    slimeconfigrequest.cpp
    slimeconfigresponse.cpp
    frtconfigrequestv3.cpp
    frtconfigresponsev3.cpp
    compressioninfo.cpp
    DEPENDS
)
