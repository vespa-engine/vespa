# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(staging_vespalib_metrics_test_app TEST
    SOURCES
    simple_metrics_test.cpp
    mock_tick.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_metrics_test_app COMMAND staging_vespalib_metrics_test_app)

vespa_add_executable(staging_vespalib_stablestore_test_app TEST
    SOURCES
    stable_store_test.cpp
    DEPENDS
    staging_vespalib
)
vespa_add_test(NAME staging_vespalib_stablestore_test_app COMMAND staging_vespalib_stablestore_test_app)
