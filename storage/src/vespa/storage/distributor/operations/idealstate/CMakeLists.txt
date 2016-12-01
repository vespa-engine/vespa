# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_distributoroperationidealstate OBJECT
    SOURCES
    idealstateoperation.cpp
    joinoperation.cpp
    mergeoperation.cpp
    splitoperation.cpp
    removebucketoperation.cpp
    setbucketstateoperation.cpp
    garbagecollectionoperation.cpp
    mergelimiter.cpp
    mergemetadata.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
