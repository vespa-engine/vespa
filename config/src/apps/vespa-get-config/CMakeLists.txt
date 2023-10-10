# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_getvespaconfig_app
    SOURCES
    getconfig.cpp
    OUTPUT_NAME vespa-get-config-bin
    INSTALL bin
    DEPENDS
    config_cloudconfig
)
