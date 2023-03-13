# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_engine_proto_converter_test_app TEST
    SOURCES
    proto_converter_test.cpp
    DEPENDS
    searchlib
    GTest::GTest
)
vespa_add_test(NAME searchlib_engine_proto_converter_test_app COMMAND searchlib_engine_proto_converter_test_app)
if(Protobuf_VERSION VERSION_LESS_EQUAL 3.12.4)
  set_source_files_properties(proto_converter_test.cpp PROPERTIES COMPILE_OPTIONS "-Wno-inline")
endif()
