# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_gid_to_lid_change_registrator_test_app TEST
    SOURCES
    gid_to_lid_change_registrator_test.cpp
    DEPENDS
    searchcore_reference
    searchcore_server
    searchcore_test
)
vespa_add_test(NAME searchcore_gid_to_lid_change_registrator_test_app COMMAND searchcore_gid_to_lid_change_registrator_test_app)
