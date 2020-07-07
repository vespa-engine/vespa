# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_extractkeywordstest_app TEST
    SOURCES
    extractkeywordstest.cpp
    simplequerystack.cpp
    simplequerystackitem.cpp
    DEPENDS
    searchsummary
)
vespa_add_test(NAME searchsummary_extractkeywordstest_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/runtests.sh
               DEPENDS searchsummary_extractkeywordstest_app)
