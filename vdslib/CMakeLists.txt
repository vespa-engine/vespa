# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    vespalib
    vespa_config
    vespa_configdefinitions
    vespa_document

    LIBS
    src/vespa/vdslib
    src/vespa/vdslib/container
    src/vespa/vdslib/distribution
    src/vespa/vdslib/state

    TESTS
    src/tests
    src/tests/container
    src/tests/distribution
    src/tests/state
)
