# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
