# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_indexmanager_test_app TEST
    SOURCES
    indexmanager_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_index
    searchcore_flushengine
    searchcore_pcommon
    GTest::GTest
)

vespa_add_test(NAME searchcore_indexmanager_test_app
    COMMAND searchcore_indexmanager_test_app)

vespa_add_executable(searchcore_fusionrunner_test_app TEST
    SOURCES
    fusionrunner_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_index
    searchcore_pcommon
)

vespa_add_test(NAME searchcore_fusionrunner_test_app
    COMMAND searchcore_fusionrunner_test_app)

vespa_add_executable(searchcore_diskindexcleaner_test_app TEST
    SOURCES
    diskindexcleaner_test.cpp
    DEPENDS
    searchcore_index
)

vespa_add_test(NAME searchcore_diskindexcleaner_test_app
    COMMAND searchcore_diskindexcleaner_test_app)

vespa_add_executable(searchcore_indexcollection_test_app TEST
    SOURCES
    indexcollection_test.cpp
    DEPENDS
    searchcore_index
    GTest::GTest
)

vespa_add_test(NAME searchcore_indexcollection_test_app
    COMMAND searchcore_indexcollection_test_app)
