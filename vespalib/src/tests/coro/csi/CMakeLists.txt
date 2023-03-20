# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(vespalib_csi_test_app TEST
    SOURCES
    csi_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_generate_protobuf_csi(vespalib_csi_test_app ./test.proto)
vespa_add_test(NAME vespalib_csi_test_app COMMAND vespalib_csi_test_app)
