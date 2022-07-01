# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

find_package(Protobuf REQUIRED)
PROTOBUF_GENERATE_CPP(storageapi_PROTOBUF_SRCS storageapi_PROTOBUF_HDRS
    protobuf/common.proto
    protobuf/feed.proto
    protobuf/inspect.proto
    protobuf/visiting.proto
    protobuf/maintenance.proto)

vespa_add_source_target(protobufgen_storageapi_mbusprot DEPENDS ${storageapi_PROTOBUF_SRCS} ${storageapi_PROTOBUF_HDRS})

vespa_suppress_warnings_for_protobuf_sources(SOURCES ${storageapi_PROTOBUF_SRCS})

# protoc explicitly annotates methods with inline, which triggers -Werror=inline when
# the header file grows over a certain size.
set_source_files_properties(protocolserialization7.cpp PROPERTIES COMPILE_FLAGS "-Wno-inline")

vespa_add_library(storageapi_mbusprot OBJECT
    SOURCES
    storagemessage.cpp
    storagecommand.cpp
    storagereply.cpp
    protocolserialization.cpp
    storageprotocol.cpp
    protocolserialization7.cpp
    ${storageapi_PROTOBUF_SRCS}
    DEPENDS
)
