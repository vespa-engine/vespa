# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_randomrow_test_app TEST
    SOURCES
    randomrow_test.cpp
    DEPENDS
    searchcore_fdispatch_search
    searchcore_util
    searchcore_fdcommon
)
vespa_add_test(NAME searchcore_randomrow_test_app COMMAND searchcore_randomrow_test_app)
