# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_sbcmd_app
    SOURCES
    sbcmd.cpp
    OUTPUT_NAME vespa-slobrok-cmd
    INSTALL bin
    DEPENDS
)
