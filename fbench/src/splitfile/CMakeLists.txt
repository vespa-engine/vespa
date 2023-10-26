# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fbench_splitfile_app
    SOURCES
    splitfile.cpp
    OUTPUT_NAME vespa-fbench-split-file
    INSTALL bin
    DEPENDS
    fbench_util
)
