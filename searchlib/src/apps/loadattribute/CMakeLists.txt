# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_loadattribute_app
    SOURCES
    loadattribute.cpp
    OUTPUT_NAME loadattribute
    INSTALL bin
    DEPENDS
    searchlib
)
