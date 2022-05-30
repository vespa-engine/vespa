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
    $<TARGET_OBJECTS:storage_storageserver_rpc>
    $<TARGET_OBJECTS:storage_storageserver>
    $<TARGET_OBJECTS:storage_distributoroperation>
    $<TARGET_OBJECTS:storage_distributoroperationexternal>
    $<TARGET_OBJECTS:storage_distributoroperationidealstate>
    $<TARGET_OBJECTS:storage_distributormaintenance>
    $<TARGET_OBJECTS:storage_distributor_bucketdb>
    $<TARGET_OBJECTS:storage_distributor>
    $<TARGET_OBJECTS:storageframework_component>
    $<TARGET_OBJECTS:storageframework_status>
    $<TARGET_OBJECTS:storageframework_thread>
    $<TARGET_OBJECTS:storageframework_clock>
    $<TARGET_OBJECTS:storageframework_clockimpl>
    $<TARGET_OBJECTS:storageframework_componentimpl>
    $<TARGET_OBJECTS:storageframework_threadimpl>
    $<TARGET_OBJECTS:storageapi_message>
    $<TARGET_OBJECTS:storageapi_buckets>
    $<TARGET_OBJECTS:storageapi_messageapi>
    $<TARGET_OBJECTS:storageapi_mbusprot>
    INSTALL lib64
    DEPENDS
)

vespa_add_target_package_dependency(storage Protobuf)
