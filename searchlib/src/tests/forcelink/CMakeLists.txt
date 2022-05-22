# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_forcelink_test_app TEST
    SOURCES
    forcelink.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_forcelink_test_app COMMAND searchlib_forcelink_test_app)
