# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_gp_ponder_nov2017_app
    SOURCES
    ponder_nov2017.cpp
    DEPENDS
    vespaeval
    GTest::gtest
)
