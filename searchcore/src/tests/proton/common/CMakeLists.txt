# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_proton_common_vespa_test_app TEST
    SOURCES
    vespa_testrunner.cpp
    selectpruner_test.cpp
    cachedselect_test.cpp
    pendinglidtracker_test.cpp
    attribute_updater_test.cpp
    state_reporter_utils_test.cpp
    document_type_inspector_test.cpp
    feedoperation_test.cpp
    documentdb_job_trackers_test.cpp
    job_load_sampler_test.cpp
    job_tracked_flush_test.cpp
    statusreport_test.cpp
    DEPENDS
    searchcore_proton_metrics
    searchcore_feedoperation
    searchcore_pcommon
    searchcore_test
    searchlib_test
)
vespa_add_test(NAME searchcore_proton_common_vespa_test_app COMMAND searchcore_proton_common_vespa_test_app)
