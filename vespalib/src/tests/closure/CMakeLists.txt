# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_closure_test_app TEST
    SOURCES
    closure_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_closure_test_app COMMAND vespalib_closure_test_app)
vespa_add_executable(vespalib_autoclosurecaller_test_app TEST
    SOURCES
    autoclosurecaller_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_autoclosurecaller_test_app COMMAND vespalib_autoclosurecaller_test_app)
