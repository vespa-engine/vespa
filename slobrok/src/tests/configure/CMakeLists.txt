# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_gencfg_app
    SOURCES
    gencfg.cpp
    DEPENDS
)
vespa_add_executable(slobrok_configure_test_app TEST
    SOURCES
    configure.cpp
    DEPENDS
    slobrok_slobrokserver
)
vespa_add_test(
    NAME slobrok_configure_test_app
    COMMAND slobrok_configure_test_app
    ENVIRONMENT "VESPA_LOG_LEVEL=\"all -debug -spam\""
)
