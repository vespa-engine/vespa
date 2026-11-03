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
configure_file(doubledelete_test.sh.in doubledelete_test.sh
    FILE_PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE
    @ONLY)
vespa_add_test(NAME vespamalloc_doubledelete_test_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_BINARY_DIR}/doubledelete_test.sh
               DEPENDS vespamalloc_doubledelete_test_app vespamalloc_expectsignal_app
                       vespamalloc vespamallocd)
