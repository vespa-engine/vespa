# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_stackdumpiteratortest_app TEST
    SOURCES
    stackdumpiteratortest.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_stackdumpiteratortest_app COMMAND searchlib_stackdumpiteratortest_app - *1000)
