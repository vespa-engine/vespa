# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(lowercasing_test_casingvariants_fastlib_app TEST
    SOURCES
    casingvariants_fastlib.cpp
    DEPENDS
)
vespa_add_executable(lowercasing_test_casingvariants_vespalib_app TEST
    SOURCES
    casingvariants_vespalib.cpp
    DEPENDS
)
vespa_add_test(NAME lowercasing_test_lowrcasing NO_VALGRIND COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/lowercasing_test.sh
               DEPENDS lowercasing_test_casingvariants_fastlib_app lowercasing_test_casingvariants_vespalib_app)
