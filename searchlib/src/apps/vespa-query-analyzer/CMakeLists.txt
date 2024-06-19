# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_vespa-query-analyzer_app
    SOURCES
    vespa-query-analyzer.cpp
    OUTPUT_NAME vespa-query-analyzer
    INSTALL bin
    DEPENDS
    vespa_searchlib
)
