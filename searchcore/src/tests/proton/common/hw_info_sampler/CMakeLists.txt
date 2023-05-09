# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_hw_info_sampler_test_app TEST
    SOURCES
    hw_info_sampler_test.cpp
    DEPENDS
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_hw_info_sampler_test_app COMMAND searchcore_hw_info_sampler_test_app)
