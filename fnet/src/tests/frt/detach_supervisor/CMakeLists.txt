# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_detach_supervisor_test_app TEST
    SOURCES
    detach_supervisor_test.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_detach_supervisor_test_app COMMAND fnet_detach_supervisor_test_app)
