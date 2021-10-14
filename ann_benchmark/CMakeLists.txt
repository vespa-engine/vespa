# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    searchlib

    LIBS
    src/vespa/ann_benchmark

    APPS

    TESTS
    src/tests/ann_benchmark
)
