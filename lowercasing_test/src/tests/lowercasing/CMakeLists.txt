# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(lowercasing_test_casingvariants_fastlib_app
    SOURCES
    casingvariants_fastlib.cpp
    DEPENDS
)
vespa_add_test(NAME lowercasing_test_lowrcasing NO_VALGRIND COMMAND sh lowercasing_test.sh)
vespa_add_executable(lowercasing_test_casingvariants_vespalib_app
    SOURCES
    casingvariants_vespalib.cpp
    DEPENDS
)
