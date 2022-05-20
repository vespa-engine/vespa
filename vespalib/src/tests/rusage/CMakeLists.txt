# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_rusage_test_app TEST
    SOURCES
    rusage_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_rusage_test_app COMMAND vespalib_rusage_test_app)
