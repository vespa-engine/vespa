# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(persistence_providerstub_test_app
    SOURCES
    providerstub_test.cpp
    DEPENDS
    persistence
)
vespa_add_executable(persistence_providerproxy_test_app
    SOURCES
    providerproxy_test.cpp
    DEPENDS
    persistence
)
vespa_add_executable(persistence_providerproxy_conformance_test_app TEST
    SOURCES
    providerproxy_conformancetest.cpp
    DEPENDS
    persistence
    persistence_persistence_conformancetest
)
vespa_add_executable(persistence_external_providerproxy_conformancetest_app
    SOURCES
    external_providerproxy_conformancetest.cpp
    OUTPUT_NAME vespa-external-providerproxy-conformancetest
    INSTALL bin
    DEPENDS
    persistence
    persistence_persistence_conformancetest
)
vespa_add_test(NAME persistence_providerproxy_conformance_test_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/proxy_test.sh
               DEPENDS persistence_providerstub_test_app persistence_providerproxy_test_app persistence_providerproxy_conformance_test_app)
