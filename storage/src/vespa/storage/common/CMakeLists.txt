# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_common OBJECT
    SOURCES
    bucket_stripe_utils.cpp
    content_bucket_space.cpp
    content_bucket_space_repo.cpp
    distributorcomponent.cpp
    global_bucket_space_distribution_converter.cpp
    messagebucket.cpp
    message_guard.cpp
    messagesender.cpp
    node_identity.cpp
    reindexing_constants.cpp
    servicelayercomponent.cpp
    statusmessages.cpp
    statusmetricconsumer.cpp
    storage_chain_builder.cpp
    storagecomponent.cpp
    storagelink.cpp
    storagelinkqueued.cpp
    DEPENDS
)
