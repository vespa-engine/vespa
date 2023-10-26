# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_thread_test_app TEST
    SOURCES
    thread.cpp
)
vespa_add_executable(vespamalloc_racemanythreads_test_app
    SOURCES
    racemanythreads.cpp
)
vespa_add_test(NAME vespamalloc_thread_test_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/thread_test.sh
               DEPENDS vespamalloc_thread_test_app vespamalloc_racemanythreads_test_app
                       vespamalloc vespamallocd)
