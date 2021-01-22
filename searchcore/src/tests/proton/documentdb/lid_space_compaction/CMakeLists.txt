# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_library(searchcore_lidspace_test STATIC
    SOURCES
    lid_space_common.cpp
)

vespa_add_executable(searchcore_lid_space_compaction_test_app TEST
    SOURCES
    lid_space_jobtest.cpp
    lid_space_compaction_test.cpp
    DEPENDS
    searchcore_lidspace_test
    searchcore_test
    searchcore_server
    searchcore_initializer
    searchcore_feedoperation
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_pcommon
    GTest::GTest
)
vespa_add_test(NAME searchcore_lid_space_compaction_test_app COMMAND searchcore_lid_space_compaction_test_app)

vespa_add_executable(searchcore_lid_space_handler_test_app TEST
    SOURCES
    lid_space_handler_test.cpp
    DEPENDS
    searchcore_lidspace_test
    searchcore_test
    searchcore_server
    searchcore_initializer
    searchcore_feedoperation
    searchcore_documentmetastore
    searchcore_bucketdb
    searchcore_pcommon
    GTest::GTest
)
vespa_add_test(NAME searchcore_lid_space_handler_test_app COMMAND searchcore_lid_space_handler_test_app)
