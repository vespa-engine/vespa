# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_teststorageserver TEST
    SOURCES
    bouncertest.cpp
    bucketintegritycheckertest.cpp
    changedbucketownershiphandlertest.cpp
    communicationmanagertest.cpp
    configurable_bucket_resolver_test.cpp
    documentapiconvertertest.cpp
    mergethrottlertest.cpp
    priorityconvertertest.cpp
    service_layer_error_listener_test.cpp
    statemanagertest.cpp
    statereportertest.cpp
    testvisitormessagesession.cpp
    DEPENDS
    storage_storageserver
    storage_testcommon
)
