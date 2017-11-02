# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_common OBJECT
    SOURCES
    bucketmessages.cpp
    bucketoperationlogger.cpp
    distributorcomponent.cpp
    messagebucket.cpp
    messagesender.cpp
    servicelayercomponent.cpp
    statusmessages.cpp
    statusmetricconsumer.cpp
    storagecomponent.cpp
    storagelink.cpp
    storagelinkqueued.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
