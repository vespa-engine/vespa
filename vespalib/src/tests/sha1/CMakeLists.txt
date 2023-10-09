# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_sha1_test_app TEST
    SOURCES
    sha1_test.cpp
    rfc_sha1.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_sha1_test_app COMMAND vespalib_sha1_test_app)
