# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(memfilepersistence
    SOURCES
    $<TARGET_OBJECTS:memfilepersistence_device>
    $<TARGET_OBJECTS:memfilepersistence_init>
    $<TARGET_OBJECTS:memfilepersistence_mapper>
    $<TARGET_OBJECTS:memfilepersistence_spi>
    $<TARGET_OBJECTS:memfilepersistence_common>
    $<TARGET_OBJECTS:memfilepersistence_memfile>
    $<TARGET_OBJECTS:memfilepersistence_tools>
    INSTALL lib64
    DEPENDS
)
