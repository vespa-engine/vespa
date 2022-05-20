# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vsm_vconfig OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(vsm_vconfig vsmfields.def) 
install_config_definition(vsmfields.def vespa.config.search.vsm.vsmfields.def) 
vespa_generate_config(vsm_vconfig vsm.def) 
install_config_definition(vsm.def vespa.config.search.vsm.vsm.def) 
vespa_generate_config(vsm_vconfig vsmsummary.def) 
install_config_definition(vsmsummary.def vespa.config.search.vsm.vsmsummary.def) 
