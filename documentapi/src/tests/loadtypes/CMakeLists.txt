# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(documentapi_loadtype_test_app TEST
    SOURCES
    loadtypetest.cpp
    DEPENDS
    documentapi
    vdstestlib
    gmock
    gtest
)
vespa_add_test(NAME documentapi_loadtype_test_app COMMAND documentapi_loadtype_test_app)
