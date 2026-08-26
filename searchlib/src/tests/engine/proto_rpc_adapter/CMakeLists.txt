# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set_source_files_properties(proto_rpc_adapter_test.cpp PROPERTIES COMPILE_FLAGS "-Wno-inline")

vespa_add_executable(searchlib_engine_proto_rpc_adapter_test_app TEST
    SOURCES
    proto_rpc_adapter_test.cpp
    DEPENDS
    vespa_searchlib
    GTest::gtest
)
vespa_add_test(NAME searchlib_engine_proto_rpc_adapter_test_app COMMAND searchlib_engine_proto_rpc_adapter_test_app)
