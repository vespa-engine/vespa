# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_fef_test_plugin OBJECT
    SOURCES
    cfgvalue.cpp
    chain.cpp
    double.cpp
    query.cpp
    setup.cpp
    staticrank.cpp
    sum.cpp
    unbox.cpp
    DEPENDS
)
