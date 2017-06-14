# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_test
    SOURCES
    document_weight_attribute_helper.cpp
    initrange.cpp
    searchiteratorverifier.cpp
    statefile.cpp
    statestring.cpp
    $<TARGET_OBJECTS:searchlib_test_fakedata>
    $<TARGET_OBJECTS:searchlib_searchlib_test_diskindex>
    DEPENDS
    searchlib
    searchlib_searchlib_test_memoryindex
)
