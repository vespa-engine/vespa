# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    config_cloudconfig
    configdefinitions
    vespaeval

    TESTS
    src/tests/attribute/config
    src/tests/schema

    LIBS
    src/vespa/searchcommon
    src/vespa/searchcommon/attribute
    src/vespa/searchcommon/common
    src/vespa/searchcommon/config
)
