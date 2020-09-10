# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vsm_vconfig OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(vsm_vconfig vespa.config.search.vsm.vsmfields.def)
vespa_generate_config(vsm_vconfig vespa.config.search.vsm.vsm.def)
vespa_generate_config(vsm_vconfig vespa.config.search.vsm.vsmsummary.def)
install_config_definitions(src/vespa/vsm/config)
