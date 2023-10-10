# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_test_fakedata OBJECT
    SOURCES
    fake_match_loop.cpp
    fakeword.cpp
    fakewordset.cpp
    fakeposting.cpp
    fakefilterocc.cpp
    fakeegcompr64filterocc.cpp
    fakememtreeocc.cpp
    fakezcfilterocc.cpp
    fakezcbfilterocc.cpp
    fpfactory.cpp
    bitencode64.cpp
    bitdecode64.cpp
    DEPENDS
)
