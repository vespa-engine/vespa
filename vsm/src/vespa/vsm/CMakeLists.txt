# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vsm
    SOURCES
    $<TARGET_OBJECTS:vsm_vconfig>
    $<TARGET_OBJECTS:vsm_vsmbase>
    $<TARGET_OBJECTS:vsm_vsmcommon>
    $<TARGET_OBJECTS:vsm_vsmsearcher>
    INSTALL lib64
    DEPENDS
)
