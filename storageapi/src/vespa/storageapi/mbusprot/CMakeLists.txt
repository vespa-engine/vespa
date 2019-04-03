# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

find_package(Protobuf REQUIRED)
PROTOBUF_GENERATE_CPP(storageapi_PROTOBUF_SRCS storageapi_PROTOBUF_HDRS protobuf/storageapi.proto)

# protoc-generated files emit compiler warnings that we normally treat as errors.
# Instead of rolling our own compiler plugin we'll pragmatically disable the noise.
set_source_files_properties(${storageapi_PROTOBUF_SRCS} PROPERTIES COMPILE_FLAGS "-Wno-suggest-override -Wno-inline")
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
    protocolserialization4_2.cpp
    protocolserialization5_0.cpp
    protocolserialization5_1.cpp
    protocolserialization5_2.cpp
    protocolserialization6_0.cpp
    protocolserialization7.cpp
    ${storageapi_PROTOBUF_SRCS}
    DEPENDS
)
