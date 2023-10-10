# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_fileutil_test_app TEST
    SOURCES
    fileutiltest.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_fileutil_test_app COMMAND vespalib_fileutil_test_app)
