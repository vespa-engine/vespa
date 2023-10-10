# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_verify_ranksetup_test_app TEST
    SOURCES
    verify_ranksetup_test.cpp
    DEPENDS
)
vespa_add_test(NAME searchcore_verify_ranksetup_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/verify_ranksetup_test.sh
               DEPENDS searchcore_verify_ranksetup_test_app searchcore_verify_ranksetup_app)
