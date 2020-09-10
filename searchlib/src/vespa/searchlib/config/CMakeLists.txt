# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_sconfig OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(searchlib_sconfig searchlib.translogserver.def)

install_config_definitions(src/vespa/searchlib/config)
