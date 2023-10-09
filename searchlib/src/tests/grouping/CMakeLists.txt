# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_grouping_test_app TEST
    SOURCES
    grouping_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_grouping_test_app COMMAND searchlib_grouping_test_app)
vespa_add_executable(searchlib_hyperloglog_test_app TEST
    SOURCES
    hyperloglog_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_hyperloglog_test_app COMMAND searchlib_hyperloglog_test_app)
vespa_add_executable(searchlib_sketch_test_app TEST
    SOURCES
    sketch_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_sketch_test_app COMMAND searchlib_sketch_test_app)
vespa_add_executable(searchlib_grouping_serialization_test_app TEST
    SOURCES
    grouping_serialization_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_grouping_serialization_test_app COMMAND searchlib_grouping_serialization_test_app)
