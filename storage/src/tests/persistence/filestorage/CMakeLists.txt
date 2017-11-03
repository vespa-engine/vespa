# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_testfilestorage TEST
    SOURCES
    deactivatebucketstest.cpp
    deletebuckettest.cpp
    filestormanagertest.cpp
    filestormodifiedbucketstest.cpp
    mergeblockingtest.cpp
    modifiedbucketcheckertest.cpp
    operationabortingtest.cpp
    sanitycheckeddeletetest.cpp
    singlebucketjointest.cpp
    DEPENDS
    storage
    storageapi
    storage_testpersistence_common
)
