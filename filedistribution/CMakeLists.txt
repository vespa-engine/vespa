# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    fnet
    frtstream
    configdefinitions
    config_cloudconfig
    staging_vespalib
    vespadefaults
    vespalib
    fileacquirer

    EXTERNAL_DEPENDS
    torrent-rasterbar

    LIBS
    src/vespa/filedistribution/rpc
    src/vespa/filedistribution/common
    src/vespa/filedistribution/manager
    src/vespa/filedistribution/distributor
    src/vespa/filedistribution/model

    TESTS
    src/tests/zkfacade
    src/tests/zkfiledbmodel
    src/tests/filedbmodelimpl
    src/tests/status
    src/tests/scheduler
    src/tests/rpc
    src/tests/common
    src/tests/filedownloader
    src/tests/lib

    APPS
    src/apps/status
    src/apps/filedistributor
)
