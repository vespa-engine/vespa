# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vbench
    SOURCES
    $<TARGET_OBJECTS:vbench_core>
    $<TARGET_OBJECTS:vbench_http>
    $<TARGET_OBJECTS:vbench_vbench_vbench>
    INSTALL lib64
    DEPENDS
)
