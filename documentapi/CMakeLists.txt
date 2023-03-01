# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    config_cloudconfig
    vespalib
    fnet
    document
    slobrok
    messagebus
    configdefinitions
    vdslib

    LIBS
    src/vespa/documentapi
    src/vespa/documentapi/messagebus
    src/vespa/documentapi/messagebus/messages
    src/vespa/documentapi/messagebus/policies

    TEST_DEPENDS
    messagebus_messagebus-test

    TESTS
    src/tests/messagebus
    src/tests/messages
    src/tests/policies
    src/tests/policyfactory
    src/tests/priority
    src/tests/replymerger
    src/tests/routablefactory
)
