# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_beta_features_test_app TEST
    SOURCES
    beta_features_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(
    NAME searchlib_beta_features_test_app
    COMMAND searchlib_beta_features_test_app
    ENVIRONMENT "VESPA_LOG_TARGET=file:vlog1.txt"
)
