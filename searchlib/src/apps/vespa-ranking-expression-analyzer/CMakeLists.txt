# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_vespa-ranking-expression-analyzer_app
    SOURCES
    vespa-ranking-expression-analyzer.cpp
    OUTPUT_NAME vespa-ranking-expression-analyzer
    INSTALL bin
    DEPENDS
    searchlib
)
