# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_distributoroperationexternal OBJECT
    SOURCES
    putoperation.cpp
    removeoperation.cpp
    updateoperation.cpp
    twophaseupdateoperation.cpp
    statbucketoperation.cpp
    removelocationoperation.cpp
    multioperationoperation.cpp
    getoperation.cpp
    multioperationoperation.cpp
    visitoroperation.cpp
    statbucketlistoperation.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
