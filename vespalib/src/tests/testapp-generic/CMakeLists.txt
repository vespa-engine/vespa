# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_testapp-generic_test_app TEST
    SOURCES
    testapp-generic.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_testapp-generic_test_app COMMAND vespalib_testapp-generic_test_app)
