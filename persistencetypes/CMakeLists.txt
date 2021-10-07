# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    staging_vespalib
    fnet
    config_cloudconfig
    document

    LIBS
    src/persistence
    src/persistence/spi
)
