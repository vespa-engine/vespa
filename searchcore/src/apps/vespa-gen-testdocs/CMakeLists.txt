# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_vespa-gen-testdocs_app
    SOURCES
    vespa-gen-testdocs.cpp
    OUTPUT_NAME vespa-gen-testdocs
    INSTALL bin
    DEPENDS
)
