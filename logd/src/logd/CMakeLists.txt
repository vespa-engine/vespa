# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# logd is the only cpp module using the protobuf files for the log protocol.
# We have choosen to generate the cpp code where it is used, instead of changing the java logserver module to also generate and export cpp code just for this case.
# If more cpp modules requires the cpp protobuf files we should move code generation to a common place.
find_package(Protobuf REQUIRED)
protobuf_generate_cpp(logd_PROTOBUF_SRCS logd_PROTOBUF_HDRS ../../../logserver/src/protobuf/log_protocol.proto)

# protoc-generated files emit compiler warnings that we normally treat as errors.
if (NOT "${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang")
  set_source_files_properties(${logd_PROTOBUF_SRCS} PROPERTIES COMPILE_FLAGS "-Wno-suggest-override")
endif()

vespa_add_library(logd STATIC
    SOURCES
    config_subscriber.cpp
    conn.cpp
    legacy_forwarder.cpp
    metrics.cpp
    proto_converter.cpp
    rpc_forwarder.cpp
    state_reporter.cpp
    watcher.cpp
    ${logd_PROTOBUF_SRCS}

    DEPENDS
)

vespa_generate_config(logd ../main/resources/configdefinitions/logd.def)
install_config_definition(../main/resources/configdefinitions/logd.def cloud.config.log.logd.def)

vespa_add_target_package_dependency(logd Protobuf)
