# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchsummary_config OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(searchsummary_config vespa.config.search.summary.juniperrc.def)

install_config_definitions(src/vespa/searchsummary/config)
