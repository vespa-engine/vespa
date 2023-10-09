# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_test(
    NAME searchcore_persistenceconformance_test_app
    COMMAND searchcore_persistenceconformance_test_app
    ENVIRONMENT "VESPA_LOG_TARGET=file:vlog.txt"
    COST 200
)
