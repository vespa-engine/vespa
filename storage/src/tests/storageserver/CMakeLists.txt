# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_teststorageserver TEST
    SOURCES
    communicationmanagertest.cpp
    statemanagertest.cpp
    documentapiconvertertest.cpp
    mergethrottlertest.cpp
    testvisitormessagesession.cpp
    bouncertest.cpp
    bucketintegritycheckertest.cpp
    priorityconvertertest.cpp
    statereportertest.cpp
    changedbucketownershiphandlertest.cpp
    DEPENDS
    storage_storageserver
    storage_testcommon
)
