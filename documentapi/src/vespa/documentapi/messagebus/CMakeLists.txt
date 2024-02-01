# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

find_package(Protobuf REQUIRED)

# .proto files are in a higher-level directory as they are shared across languages
set(documentapi_messagebus_PROTOBUF_REL_PATH "../../../protobuf")
PROTOBUF_GENERATE_CPP(documentapi_messagebus_PROTOBUF_SRCS documentapi_messagebus_PROTOBUF_HDRS
    "${documentapi_messagebus_PROTOBUF_REL_PATH}/docapi_common.proto"
    "${documentapi_messagebus_PROTOBUF_REL_PATH}/docapi_feed.proto"
    "${documentapi_messagebus_PROTOBUF_REL_PATH}/docapi_inspect.proto"
    "${documentapi_messagebus_PROTOBUF_REL_PATH}/docapi_visiting.proto")

vespa_add_source_target(protobufgen_documentapi_messagebus DEPENDS
    ${documentapi_messagebus_PROTOBUF_SRCS}
    ${documentapi_messagebus_PROTOBUF_HDRS})

vespa_suppress_warnings_for_protobuf_sources(SOURCES ${documentapi_messagebus_PROTOBUF_SRCS})

# protoc explicitly annotates methods with inline, which triggers -Werror=inline when
# the header file grows over a certain size.
set_source_files_properties(routable_factories_8.cpp PROPERTIES COMPILE_FLAGS "-Wno-inline")

vespa_add_library(documentapi_documentapimessagebus OBJECT
    SOURCES
    documentprotocol.cpp
    replymerger.cpp
    routable_factories_8.cpp
    routablefactories60.cpp
    routablerepository.cpp
    routingpolicyfactories.cpp
    routingpolicyrepository.cpp
    ${documentapi_messagebus_PROTOBUF_SRCS}
    DEPENDS
    documentapi_documentapipolicies
)
