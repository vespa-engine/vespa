# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespadefaults
    fastos
    configdefinitions
    config_cloudconfig
    vespalog
    document
    documentapi
    vespalib
    staging_vespalib

    LIBS
    src/vespa/vespaclient/clusterlist

    APPS
    src/vespa/vespaclient/spoolmaster
    src/vespa/vespaclient/vdsstates
    src/vespa/vespaclient/vespadoclocator
    src/vespa/vespaclient/vesparoute
)
