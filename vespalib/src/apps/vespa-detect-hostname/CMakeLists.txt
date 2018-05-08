# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_vespa-detect-hostname_app
    SOURCES
    detect_hostname.cpp
    OUTPUT_NAME vespa-detect-hostname
    INSTALL bin
    DEPENDS
    vespalib
)
