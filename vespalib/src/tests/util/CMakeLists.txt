# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(vespalib_util_gtest_runner_test_app TEST
    SOURCES
    gtest_runner.cpp
    bfloat16_test.cpp
    cgroup_resource_limits_test.cpp
    file_area_freelist_test.cpp
    generation_hold_list_test.cpp
    generationhandler_test.cpp
    memory_trap_test.cpp
    mmap_file_allocator_factory_test.cpp
    mmap_file_allocator_test.cpp
    rcuvector_test.cpp
    size_literals_test.cpp
    static_string_test.cpp
    string_escape_test.cpp
    DEPENDS
    vespalib
    onnxruntime
    GTest::GTest
)

vespa_add_test( NAME vespalib_util_gtest_runner_test_app COMMAND vespalib_util_gtest_runner_test_app)
