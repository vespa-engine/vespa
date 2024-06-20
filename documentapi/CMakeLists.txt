# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    vespa_config
    vespalib
    vespa_fnet
    vespa_document
    vespa_slobrok
    vespa_messagebus
    vespa_configdefinitions
    vespa_vdslib

    LIBS
    src/vespa/documentapi
    src/vespa/documentapi/messagebus
    src/vespa/documentapi/messagebus/messages
    src/vespa/documentapi/messagebus/policies

    TEST_DEPENDS
    vespa_messagebus-test

    TESTS
    src/tests/messagebus
    src/tests/messages
    src/tests/policies
    src/tests/policyfactory
    src/tests/priority
    src/tests/replymerger
    src/tests/routablefactory
)
