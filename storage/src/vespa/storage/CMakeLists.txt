# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage
    SOURCES
    $<TARGET_OBJECTS:storage_bucketdb>
    $<TARGET_OBJECTS:storage_common>
    $<TARGET_OBJECTS:storage_storageconfig>
    $<TARGET_OBJECTS:storage_hostreporter>
    $<TARGET_OBJECTS:storage_filestorpersistence>
    $<TARGET_OBJECTS:storage_spersistence>
    $<TARGET_OBJECTS:storage_storageutil>
    $<TARGET_OBJECTS:storage_visitor>
    $<TARGET_OBJECTS:storage_thread>
    $<TARGET_OBJECTS:storage_status>
    $<TARGET_OBJECTS:storage_component>
    INSTALL lib64
    DEPENDS
)
