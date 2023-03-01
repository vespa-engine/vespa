# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    vespalib
    searchlib

    APPS
    src/binref

    TESTS
    src/tests/lowercasing
)
