# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_signalhandler_test_app TEST
    SOURCES
    signalhandler_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_signalhandler_test_app NO_VALGRIND COMMAND vespalib_signalhandler_test_app)
vespa_add_executable(vespalib_victim_app
    SOURCES
    victim.cpp
    DEPENDS
    vespalib
)
