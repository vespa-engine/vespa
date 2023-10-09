# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_method_pt_test_app TEST
    SOURCES
    method_pt.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_method_pt_test_app COMMAND fnet_method_pt_test_app)
