# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespadefaults
    config_cloudconfig
    vbench
    vespalib

    LIBS
    src/lib

    APPS
    src/apps/configstatus
    src/apps/modelinspect

    TESTS
    src/tests/config_status
    src/tests/model_inspect
    src/tests/tags
    src/tests/host_filter
    )
