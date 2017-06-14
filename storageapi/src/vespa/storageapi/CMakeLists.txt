# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageapi
    SOURCES
    $<TARGET_OBJECTS:storageapi_message>
    $<TARGET_OBJECTS:storageapi_buckets>
    $<TARGET_OBJECTS:storageapi_messageapi>
    $<TARGET_OBJECTS:storageapi_mbusprot>
    INSTALL lib64
    DEPENDS
)
