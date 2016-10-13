# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageapi_testmbusprot
    SOURCES
    storageprotocoltest.cpp
    DEPENDS
    storageapi
    vdstestlib
)
