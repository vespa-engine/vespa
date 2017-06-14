# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_indexmanager_test_app TEST
    SOURCES
    indexmanager_test.cpp
    DEPENDS
    searchcore_server
    searchcore_index
    searchcore_flushengine
    searchcore_pcommon
    searchcore_util
)
vespa_add_executable(searchcore_fusionrunner_test_app TEST
    SOURCES
    fusionrunner_test.cpp
    DEPENDS
    searchcore_server
    searchcore_index
    searchcore_pcommon
    searchcore_util
)
vespa_add_executable(searchcore_diskindexcleaner_test_app TEST
    SOURCES
    diskindexcleaner_test.cpp
    DEPENDS
    searchcore_index
)
vespa_add_executable(searchcore_indexcollection_test_app TEST
    SOURCES
    indexcollection_test.cpp
    DEPENDS
    searchcore_index
)
vespa_add_test(NAME searchcore_index_test COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/index_test.sh 
               DEPENDS searchcore_indexmanager_test_app searchcore_fusionrunner_test_app searchcore_diskindexcleaner_test_app searchcore_indexcollection_test_app)
