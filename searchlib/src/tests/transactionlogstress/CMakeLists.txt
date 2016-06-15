# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_translogstress_app
    SOURCES
    translogstress.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_translogstress_app COMMAND searchlib_translogstress_app BENCHMARK)
