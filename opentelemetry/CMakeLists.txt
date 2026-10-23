# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespa_configdefinitions
    vespalib
    vespalog

    LIBS
    src/vespa/opentelemetry

    TESTS
    src/tests/opentelemetry
)
