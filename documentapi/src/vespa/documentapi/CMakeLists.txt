# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespa_documentapi
    SOURCES
    $<TARGET_OBJECTS:documentapi_documentapimessagebus>
    $<TARGET_OBJECTS:documentapi_documentapimessages>
    $<TARGET_OBJECTS:documentapi_documentapipolicies>
    INSTALL lib64
    DEPENDS
    protobuf::libprotobuf
)

vespa_add_target_package_dependency(vespa_documentapi Protobuf)
