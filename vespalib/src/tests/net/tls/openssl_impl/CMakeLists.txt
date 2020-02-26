# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_net_tls_openssl_impl_test_app TEST
    SOURCES
    openssl_impl_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_net_tls_openssl_impl_test_app COMMAND vespalib_net_tls_openssl_impl_test_app)

