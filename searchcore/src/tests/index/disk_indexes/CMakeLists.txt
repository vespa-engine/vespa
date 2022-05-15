# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcorespi_disk_indexes_test_app
    SOURCES
    disk_indexes_test.cpp
    DEPENDS
    searchcorespi
    GTest::GTest
)
vespa_add_test(NAME searchcorespi_disk_indexes_test_app COMMAND searchcorespi_disk_indexes_test_app)
