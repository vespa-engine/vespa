# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(configd_messages_test_app TEST
    SOURCES
    messages.cpp
    DEPENDS
    vespalog
    vespalib
)
vespa_add_test(NAME configd_messages_test_app COMMAND configd_messages_test_app)
