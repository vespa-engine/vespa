# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    config_cloudconfig

    APPS
    src/apps/logd

    LIBS
    src/logd

    TESTS
    src/tests/forward
    src/tests/info
    src/tests/rotate
)
