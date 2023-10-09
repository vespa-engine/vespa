# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_vespa-index-inspect_app
    SOURCES
    vespa-index-inspect.cpp
    OUTPUT_NAME vespa-index-inspect
    INSTALL bin
    DEPENDS
    searchlib
)
