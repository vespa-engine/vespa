# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_overwrite_test_app TEST
    SOURCES
    overwrite.cpp
    DEPENDS
)
file(COPY vespamalloc.conf DESTINATION ${CMAKE_CURRENT_BINARY_DIR})
vespa_add_executable(vespamalloc_expectsignal-overwrite_app
    SOURCES
    expectsignal.cpp
    DEPENDS
)
vespa_add_test(NAME vespamalloc_overwrite_test_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/overwrite_test.sh
               DEPENDS vespamalloc_overwrite_test_app vespamalloc_expectsignal-overwrite_app
                       vespamalloc vespamalloc_vespamallocd)
