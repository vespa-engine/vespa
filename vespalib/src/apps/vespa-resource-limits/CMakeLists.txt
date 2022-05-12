# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_vespa-resource-limits_app
    SOURCES
    resource_limits.cpp
    OUTPUT_NAME vespa-resource-limits
    INSTALL bin
    DEPENDS
    vespalib
)
