# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_proton_disk_layout_test_app TEST
    SOURCES
    proton_disk_layout_test.cpp
    DEPENDS
    searchcore_test
    searchcore_server
    searchcore_fconfig
)
vespa_add_test(NAME searchcore_proton_disk_layout_test_app COMMAND searchcore_proton_disk_layout_test_app)
