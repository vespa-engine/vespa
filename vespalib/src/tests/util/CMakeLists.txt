# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Runner for unit tests written in gtest.
# NOTE: All new test classes should be added here.
vespa_add_executable(vespalib_util_gtest_runner_test_app TEST
    SOURCES
    gtest_runner.cpp
    bfloat16_test.cpp
    bits_test.cpp
    cgroup_resource_limits_test.cpp
    crc_test.cpp
    crypto_test.cpp
    eventbarrier.cpp
    file_area_freelist_test.cpp
    generation_hold_list_test.cpp
    generationhandler_test.cpp
    json.cpp
    issue_test.cpp
    memory_trap_test.cpp
    mmap_file_allocator_factory_test.cpp
    mmap_file_allocator_test.cpp
    nexus_test.cpp
    printabletest.cpp
    ptrholder.cpp
    random_test.cpp
    rcuvector_test.cpp
    ref_counted_test.cpp
    require_test.cpp
    size_literals_test.cpp
    small_vector_test.cpp
    static_string_test.cpp
    string_escape_test.cpp
    time_test.cpp
    typify_test.cpp
    xmlserializabletest.cpp
    DEPENDS
    vespalib
    onnxruntime
    GTest::GTest
)

vespa_add_test( NAME vespalib_util_gtest_runner_test_app COMMAND vespalib_util_gtest_runner_test_app)
