# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(memfilepersistence_testdevices
    SOURCES
    mountpointlisttest.cpp
    devicemanagertest.cpp
    devicestest.cpp
    devicemappertest.cpp
    partitionmonitortest.cpp
    DEPENDS
    memfilepersistence
)
