# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(streamingvisitors
    SOURCES
    hitcollector.cpp
    indexenvironment.cpp
    matching_elements_filler.cpp
    queryenvironment.cpp
    querytermdata.cpp
    querywrapper.cpp
    rankmanager.cpp
    rankprocessor.cpp
    searchenvironment.cpp
    searchvisitor.cpp
    $<TARGET_OBJECTS:vsm_vconfig>
    $<TARGET_OBJECTS:vsm_vsmbase>
    $<TARGET_OBJECTS:vsm_vsmcommon>
    $<TARGET_OBJECTS:vsm_vsmsearcher>
    INSTALL lib64
    DEPENDS
)
