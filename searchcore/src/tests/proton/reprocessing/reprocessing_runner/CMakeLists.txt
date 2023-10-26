# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_reprocessing_runner_test_app TEST
    SOURCES
    reprocessing_runner_test.cpp
    DEPENDS
    searchcore_reprocessing
)
vespa_add_test(NAME searchcore_reprocessing_runner_test_app COMMAND searchcore_reprocessing_runner_test_app)
