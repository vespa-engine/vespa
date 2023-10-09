# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(config_cloudconfig
    SOURCES
    $<TARGET_OBJECTS:config_common>
    $<TARGET_OBJECTS:config_subscription>
    $<TARGET_OBJECTS:config_configgen>
    $<TARGET_OBJECTS:config_raw>
    $<TARGET_OBJECTS:config_file>
    $<TARGET_OBJECTS:config_file_acquirer>
    $<TARGET_OBJECTS:config_frt>
    $<TARGET_OBJECTS:config_helper>
    $<TARGET_OBJECTS:config_print>
    $<TARGET_OBJECTS:config_set>
    $<TARGET_OBJECTS:config_retriever>
    INSTALL lib64
    DEPENDS
)
