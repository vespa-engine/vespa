# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_vespa-gen-testdocs_app
    SOURCES
    vespa-gen-testdocs.cpp
    OUTPUT_NAME vespa-gen-testdocs
    INSTALL bin
    DEPENDS
)
vespa_add_target_system_dependency(searchcore_vespa-gen-testdocs_app crypto)
