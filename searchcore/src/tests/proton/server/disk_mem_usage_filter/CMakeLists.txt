# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_disk_mem_usage_filter_test_app TEST
    SOURCES
    disk_mem_usage_filter_test.cpp
    DEPENDS
    searchcore_server
    GTest::GTest
)
vespa_add_test(NAME searchcore_disk_mem_usage_filter_test_app COMMAND searchcore_disk_mem_usage_filter_test_app)
