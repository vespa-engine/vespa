# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(protoc-gen-frt
    SOURCES
    frt-protoc.cpp
    EXTERNAL_DEPENDS
    protoc
)
