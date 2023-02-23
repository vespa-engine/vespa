# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalib
    fnet
    configdefinitions

    LIBS
    src/vespa/slobrok
    src/vespa/slobrok/server

    APPS
    src/apps/check_slobrok
    src/apps/sbcmd
    src/apps/slobrok

    TESTS
    src/tests/backoff
    src/tests/configure
    src/tests/local_rpc_monitor_map
    src/tests/mirrorapi
    src/tests/registerapi
    src/tests/rpc_mapping_monitor
    src/tests/service_map_history
    src/tests/service_map_mirror
    src/tests/standalone
    src/tests/startsome
    src/tests/startup
    src/tests/union_service_map
)
