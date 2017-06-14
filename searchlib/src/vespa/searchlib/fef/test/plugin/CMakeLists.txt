# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_fef_test_plugin OBJECT
    SOURCES
    double.cpp
    sum.cpp
    staticrank.cpp
    chain.cpp
    cfgvalue.cpp
    query.cpp
    setup.cpp
    DEPENDS
)
