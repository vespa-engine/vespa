# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(documentapi_policyfactory_test_app TEST
    SOURCES
    policyfactory.cpp
    DEPENDS
    documentapi
)
vespa_add_test(NAME documentapi_policyfactory_test_app COMMAND documentapi_policyfactory_test_app)
