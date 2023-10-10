# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_testkit-subset_test_app TEST
    SOURCES
    testkit-subset_test.cpp
    testkit-subset_extra.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_testkit-subset_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/testkit-subset_test.sh  
               DEPENDS vespalib_testkit-subset_test_app)
