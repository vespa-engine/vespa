# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_disk_mem_usage_filter_test_app TEST
    SOURCES
    disk_mem_usage_filter_test.cpp
    DEPENDS
    searchcore_server
)
vespa_add_target_system_dependency(searchcore_disk_mem_usage_filter_test_app boost boost_system-mt-d)
vespa_add_target_system_dependency(searchcore_disk_mem_usage_filter_test_app boost boost_filesystem-mt-d)
vespa_add_test(NAME searchcore_disk_mem_usage_filter_test_app COMMAND searchcore_disk_mem_usage_filter_test_app)
