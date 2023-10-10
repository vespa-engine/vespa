# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(messagebus_printversion_app
    SOURCES
    printversion.cpp
    DEPENDS
    messagebus
)
