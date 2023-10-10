# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

find_package(Protobuf REQUIRED)
PROTOBUF_GENERATE_CPP(storage_storageserver_rpc_PROTOBUF_SRCS storage_storageserver_rpc_PROTOBUF_HDRS
    protobuf/rpc_envelope.proto
)

vespa_add_source_target(protobufgen_storage_storageserver_rpc DEPENDS
    ${storage_storageserver_rpc_PROTOBUF_SRCS}
    ${storage_storageserver_rpc_PROTOBUF_HDRS})

vespa_suppress_warnings_for_protobuf_sources(SOURCES ${storage_storageserver_rpc_PROTOBUF_SRCS})

vespa_add_library(storage_storageserver_rpc OBJECT
    SOURCES
    caching_rpc_target_resolver.cpp
    cluster_controller_api_rpc_service.cpp
    message_codec_provider.cpp
    rpc_target_pool.cpp
    shared_rpc_resources.cpp
    slime_cluster_state_bundle_codec.cpp
    storage_api_rpc_service.cpp
    ${storage_storageserver_rpc_PROTOBUF_SRCS}
    DEPENDS
)
