# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(lowercasing_test_hello-world_app
    SOURCES
    hello-world.cpp
    INSTALL bin
    DEPENDS
    lowercasing_test_hello-world-lib
)
