# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

find_package(Protobuf REQUIRED)
protobuf_generate_cpp(searchlib_engine_PROTOBUF_SRCS searchlib_engine_PROTOBUF_HDRS ../../../../src/protobuf/search_protocol.proto)

# protoc-generated files emit compiler warnings that we normally treat as errors.
if (NOT "${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang")
  set_source_files_properties(${searchlib_engine_PROTOBUF_SRCS}
    PROPERTIES COMPILE_FLAGS "-Wno-suggest-override")
endif()

vespa_add_library(searchlib_engine OBJECT
    SOURCES
    docsumapi.cpp
    docsumreply.cpp
    docsumrequest.cpp
    errorcodes.cpp
    lazy_source.cpp
    monitorreply.cpp
    monitorrequest.cpp
    packetconverter.cpp
    propertiesmap.cpp
    proto_converter.cpp
    proto_rpc_adapter.cpp
    request.cpp
    searchreply.cpp
    searchrequest.cpp
    trace.cpp
    transport_metrics.cpp
    transportserver.cpp
    ${searchlib_engine_PROTOBUF_SRCS}
    DEPENDS
)
