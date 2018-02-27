# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_arrayref_test_app TEST
    SOURCES
    arrayref_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_arrayref_test_app COMMAND vespalib_arrayref_test_app)
