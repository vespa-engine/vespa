# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_distributoroperationexternal OBJECT
    SOURCES
    getoperation.cpp
    putoperation.cpp
    removelocationoperation.cpp
    removeoperation.cpp
    statbucketlistoperation.cpp
    statbucketoperation.cpp
    twophaseupdateoperation.cpp
    updateoperation.cpp
    visitoroperation.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
