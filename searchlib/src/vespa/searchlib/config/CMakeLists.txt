# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_sconfig OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(searchlib_sconfig translogserver.def)
install_config_definition(translogserver.def searchlib.translogserver.def)
