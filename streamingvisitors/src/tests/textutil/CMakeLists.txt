# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vsm_textutil_test_app TEST
    SOURCES
    textutil.cpp
    DEPENDS
    streamingvisitors
)
vespa_add_test(NAME vsm_textutil_test_app COMMAND vsm_textutil_test_app)
