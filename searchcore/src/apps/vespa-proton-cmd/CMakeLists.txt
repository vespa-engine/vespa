# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_vespa-proton-cmd_app
    SOURCES
    vespa-proton-cmd.cpp
    OUTPUT_NAME vespa-proton-cmd-bin
    INSTALL bin
    DEPENDS
)
