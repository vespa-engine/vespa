# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_simple_hash_map_test_app
    SOURCES
    simple_hash_map_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_simple_hash_map_test_app COMMAND vespalib_simple_hash_map_test_app)
