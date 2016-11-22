# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_common OBJECT
    SOURCES
    statusmetricconsumer.cpp
    storagelink.cpp
    storagelinkqueued.cpp
    bucketoperationlogger.cpp
    messagebucketid.cpp
    bucketmessages.cpp
    statusmessages.cpp
    messagesender.cpp
    storagecomponent.cpp
    servicelayercomponent.cpp
    distributorcomponent.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
