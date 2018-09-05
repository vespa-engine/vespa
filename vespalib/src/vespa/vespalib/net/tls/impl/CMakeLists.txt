# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_net_tls_impl OBJECT
    SOURCES
    openssl_tls_context_impl.cpp
    openssl_crypto_codec_impl.cpp
    DEPENDS
)
vespa_add_target_package_dependency(vespalib_vespalib_net_tls_impl OpenSSL)

