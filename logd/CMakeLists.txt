# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    vespalib
    config_cloudconfig

    APPS
    src/apps/logd

    LIBS
    src/logd

    TESTS
    src/tests/empty_forwarder
    src/tests/proto_converter
    src/tests/rotate
    src/tests/rpc_forwarder
    src/tests/watcher
)

vespa_install_script(src/apps/retention/retention-enforcer.sh vespa-retention-enforcer sbin)
