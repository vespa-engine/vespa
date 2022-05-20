# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_documentdb_job_trackers_test_app TEST
    SOURCES
    documentdb_job_trackers_test.cpp
    DEPENDS
    searchcore_proton_metrics
    searchcore_test
)
vespa_add_test(NAME searchcore_documentdb_job_trackers_test_app COMMAND searchcore_documentdb_job_trackers_test_app)
