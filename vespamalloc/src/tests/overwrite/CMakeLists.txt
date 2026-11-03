# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespamalloc_overwrite_test_app TEST
    SOURCES
    overwrite.cpp
    DEPENDS
    GTest::gtest
)
file(COPY vespamalloc.conf DESTINATION ${CMAKE_CURRENT_BINARY_DIR})
vespa_add_executable(vespamalloc_expectsignal-overwrite_app
    SOURCES
    expectsignal.cpp
    DEPENDS
)
configure_file(overwrite_test.sh.in overwrite_test.sh
    FILE_PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE
    @ONLY)
vespa_add_test(NAME vespamalloc_overwrite_test_app NO_VALGRIND COMMAND ${CMAKE_CURRENT_BINARY_DIR}/overwrite_test.sh
               DEPENDS vespamalloc_overwrite_test_app vespamalloc_expectsignal-overwrite_app
                       vespamalloc vespamallocd)

vespa_add_executable(vespamalloc_overwrite_test_with_vespamallocd_app TEST
    SOURCES
    overwrite.cpp
    DEPENDS
    vespamallocd
    GTest::gtest
)
vespa_add_test(NAME vespamalloc_overwrite_test_with_vespamallocd_app NO_VALGRIND COMMAND vespamalloc_overwrite_test_with_vespamallocd_app testmemoryfill)
