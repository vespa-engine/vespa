# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    config_cloudconfig
    vespalib
    staging_vespalib
    fnet
    slobrok_slobrokserver

    LIBS
    src/vespa/messagebus
    src/vespa/messagebus/network
    src/vespa/messagebus/routing
    src/vespa/messagebus/testlib

    APPS
    src/apps/printversion

    TESTS
    src/tests
)
