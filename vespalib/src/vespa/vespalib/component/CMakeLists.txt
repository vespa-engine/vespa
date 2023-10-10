# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_component OBJECT
    SOURCES
    version.cpp
    versionspecification.cpp
    vtag.cpp
    DEPENDS
)
set_source_files_properties(vtag.cpp PROPERTIES COMPILE_FLAGS "${VTAG_DEFINES}")
