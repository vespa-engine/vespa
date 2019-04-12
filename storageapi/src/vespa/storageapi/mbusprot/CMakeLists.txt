# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageapi_mbusprot OBJECT
    SOURCES
    storagemessage.cpp
    storagecommand.cpp
    storagereply.cpp
    protocolserialization.cpp
    storageprotocol.cpp
    protocolserialization4_2.cpp
    protocolserialization5_0.cpp
    protocolserialization5_1.cpp
    protocolserialization5_2.cpp
    protocolserialization6_0.cpp
    DEPENDS
)
