# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_doubledelete_test_app TEST
    SOURCES
    doubledelete.cpp
    DEPENDS
)
vespa_add_executable(vespamalloc_expectsignal_app
    SOURCES
    expectsignal.cpp
    DEPENDS
)
vespa_add_test(NAME vespamalloc_doubledelete_test_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/doubledelete_test.sh
               DEPENDS vespamalloc_doubledelete_test_app vespamalloc_expectsignal_app
                       vespamalloc vespamallocd)
