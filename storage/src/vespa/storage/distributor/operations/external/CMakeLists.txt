# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_distributoroperationexternal OBJECT
    SOURCES
    check_condition.cpp
    getoperation.cpp
    intermediate_message_sender.cpp
    newest_replica.cpp
    putoperation.cpp
    read_for_write_visitor_operation.cpp
    removelocationoperation.cpp
    removeoperation.cpp
    statbucketlistoperation.cpp
    statbucketoperation.cpp
    twophaseupdateoperation.cpp
    updateoperation.cpp
    visitoroperation.cpp
    DEPENDS
)
