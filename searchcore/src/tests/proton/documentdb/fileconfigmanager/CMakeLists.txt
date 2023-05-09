# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_fileconfigmanager_test_app TEST
    SOURCES
    fileconfigmanager_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_fileconfigmanager_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/fileconfigmanager_test.sh
               DEPENDS searchcore_fileconfigmanager_test_app)
vespa_generate_config(searchcore_fileconfigmanager_test_app mycfg.def)
