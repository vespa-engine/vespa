# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespadefaults
    configdefinitions
    config_cloudconfig
    vespalog
    document
    documentapi
    vespalib

    LIBS
    src/vespa/vespaclient/clusterlist

    APPS
    src/vespa/vespaclient/vesparoute
)
