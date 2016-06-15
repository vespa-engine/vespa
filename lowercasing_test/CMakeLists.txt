# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    searchlib

    APPS
    src/binref
    src/java

    TESTS
    src/grouping_test/hello-world
    src/grouping_test/hello-world-lib
    src/tests/hello-world
    src/tests/lowercasing
)
