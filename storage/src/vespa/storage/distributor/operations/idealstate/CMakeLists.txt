# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_distributoroperationidealstate OBJECT
    SOURCES
    garbagecollectionoperation.cpp
    idealstateoperation.cpp
    joinoperation.cpp
    mergelimiter.cpp
    mergemetadata.cpp
    mergeoperation.cpp
    removebucketoperation.cpp
    setbucketstateoperation.cpp
    splitoperation.cpp
    DEPENDS
)
