# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fastos_cpumonitor
    SOURCES
    cpumonitor.cpp
    OUTPUT_NAME cpumonitor
    INSTALL bin
    DEPENDS
    pthread
)
