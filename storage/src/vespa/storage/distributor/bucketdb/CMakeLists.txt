# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_bucketdatabase OBJECT
    SOURCES
    bucketinfo.cpp
    bucketcopy.cpp
    bucketdatabase.cpp
    judybucketdatabase.cpp
    mapbucketdatabase.cpp
    bucketdbmetricupdater.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
