# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(streamingvisitors_querywrapper_test_app TEST
    SOURCES
    querywrapper_test.cpp
    DEPENDS
    streamingvisitors
)
vespa_add_test(NAME streamingvisitors_querywrapper_test_app COMMAND streamingvisitors_querywrapper_test_app)
