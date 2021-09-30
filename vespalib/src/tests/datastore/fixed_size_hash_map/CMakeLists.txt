# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_fixed_size_hash_map_test_app
    SOURCES
    fixed_size_hash_map_test.cpp
    DEPENDS
    vespalib
    GTest::GTest
)
vespa_add_test(NAME vespalib_fixed_size_hash_map_test_app COMMAND vespalib_fixed_size_hash_map_test_app)
