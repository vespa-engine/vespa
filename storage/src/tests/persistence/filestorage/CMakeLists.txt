# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testfilestorage TEST
    SOURCES
    filestormanagertest.cpp
    operationabortingtest.cpp
    mergeblockingtest.cpp
    sanitycheckeddeletetest.cpp
    deactivatebucketstest.cpp
    modifiedbucketcheckertest.cpp
    filestormodifiedbucketstest.cpp
    deletebuckettest.cpp
    singlebucketjointest.cpp
    DEPENDS
    storage
    storageapi
    storage_testpersistence_common
)
