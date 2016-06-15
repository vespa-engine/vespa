# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vdstestlib
    SOURCES
    $<TARGET_OBJECTS:vdstestlib_vdstestlib_cppunit>
    INSTALL lib64
    DEPENDS
    cppunit
)
