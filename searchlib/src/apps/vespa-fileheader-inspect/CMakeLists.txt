# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_vespa-fileheader-inspect_app
    SOURCES
    vespa-fileheader-inspect.cpp
    OUTPUT_NAME vespa-fileheader-inspect
    INSTALL bin
    DEPENDS
    searchlib
)
