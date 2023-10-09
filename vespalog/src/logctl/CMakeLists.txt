# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalog_vespa-logctl_app
    SOURCES
    logctl.cpp
    OUTPUT_NAME vespa-logctl
    INSTALL bin
    DEPENDS
    vespalog
)
