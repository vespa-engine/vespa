# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_postinglistbm_app TEST
    SOURCES
    postinglistbm.cpp
    andstress.cpp
    DEPENDS
    searchlib_test
    searchlib
)
vespa_add_test(NAME searchlib_postinglistbm_app NO_VALGRIND COMMAND searchlib_postinglistbm_app -q -a)
