# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_vespa-validate-hostname_app
    SOURCES
    validate_hostname.cpp
    OUTPUT_NAME vespa-validate-hostname
    INSTALL bin
    DEPENDS
    vespalib
)
