# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_features_fieldmatch OBJECT
    SOURCES
    computer.cpp
    metrics.cpp
    params.cpp
    segmentstart.cpp
    simplemetrics.cpp
    DEPENDS
)
