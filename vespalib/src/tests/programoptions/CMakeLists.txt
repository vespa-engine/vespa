# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_programoptions_test_app TEST
    SOURCES
    programoptions_test.cpp
    programoptions_testutils.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_programoptions_test_app COMMAND vespalib_programoptions_test_app)
