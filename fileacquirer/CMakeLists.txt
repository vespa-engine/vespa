# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    vespalib
    vespa_config

    LIBS
    src/vespa/fileacquirer
)

install_config_definitions()
