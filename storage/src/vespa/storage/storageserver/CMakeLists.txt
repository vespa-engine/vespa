# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_storageserver
    SOURCES
    priorityconverter.cpp
    bucketintegritychecker.cpp
    bouncer.cpp
    messagesink.cpp
    fnetlistener.cpp
    rpcrequestwrapper.cpp
    communicationmanager.cpp
    statemanager.cpp
    documentapiconverter.cpp
    opslogger.cpp
    mergethrottler.cpp
    messageallocationtypes.cpp
    storagenodecontext.cpp
    distributornodecontext.cpp
    servicelayernodecontext.cpp
    storagenode.cpp
    distributornode.cpp
    servicelayernode.cpp
    statereporter.cpp
    changedbucketownershiphandler.cpp
    INSTALL lib64
    DEPENDS
    storage
    storage_distributor
)
