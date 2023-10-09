# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(documentapi_messages60_test_app TEST
    SOURCES
    testbase.cpp
    messages60test.cpp
    messages60app.cpp
    DEPENDS
    documentapi
)
vespa_add_test(NAME documentapi_messages60_test_app COMMAND documentapi_messages60_test_app)
vespa_add_executable(documentapi_error_codes_test_app_app TEST
    SOURCES
    error_codes_test.cpp
    DEPENDS
    documentapi
)
vespa_add_test(NAME documentapi_error_codes_test_app_app COMMAND documentapi_error_codes_test_app_app)
