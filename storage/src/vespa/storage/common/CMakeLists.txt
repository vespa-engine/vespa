# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_common OBJECT
    SOURCES
    bucketmessages.cpp
    bucketoperationlogger.cpp
    cluster_state_bundle.cpp
    content_bucket_space.cpp
    content_bucket_space_repo.cpp
    distributorcomponent.cpp
    global_bucket_space_distribution_converter.cpp
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
