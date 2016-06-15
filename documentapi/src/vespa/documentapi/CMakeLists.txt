# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(documentapi
    SOURCES
    $<TARGET_OBJECTS:documentapi_documentapimessagebus>
    $<TARGET_OBJECTS:documentapi_documentapimessages>
    $<TARGET_OBJECTS:documentapi_documentapipolicies>
    $<TARGET_OBJECTS:documentapi_documentapisystemstate>
    $<TARGET_OBJECTS:documentapi_documentapiloadtypes>
    INSTALL lib64
    DEPENDS
)
