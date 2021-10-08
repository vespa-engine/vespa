# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalib

    LIBS
    src/vespa/fastlib/io
    src/vespa/fastlib/io/tests
    src/vespa/fastlib/testsuite
    src/vespa/fastlib/text
    src/vespa/fastlib/text/apps
    src/vespa/fastlib/text/tests
    src/vespa/fastlib/util
    src/vespa/fastlib/util/tests
    src/vespa/packages
)
