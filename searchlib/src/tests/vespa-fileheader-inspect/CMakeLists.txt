# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_vespa-fileheader-inspect_test_app TEST
    SOURCES
    vespa-fileheader-inspect_test.cpp
    DEPENDS
    searchlib
    AFTER
    searchlib_vespa-fileheader-inspect_app
)
vespa_add_test(NAME searchlib_vespa-fileheader-inspect_test_app COMMAND searchlib_vespa-fileheader-inspect_test_app)
