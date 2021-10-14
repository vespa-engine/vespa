# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_testurl_app TEST
    SOURCES
    testurl.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_testurl_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/dotest.sh
               DEPENDS searchlib_testurl_app)
