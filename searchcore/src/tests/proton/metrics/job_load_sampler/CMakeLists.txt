# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_job_load_sampler_test_app TEST
    SOURCES
    job_load_sampler_test.cpp
    DEPENDS
    searchcore_proton_metrics
)
vespa_add_test(NAME searchcore_job_load_sampler_test_app COMMAND searchcore_job_load_sampler_test_app)
