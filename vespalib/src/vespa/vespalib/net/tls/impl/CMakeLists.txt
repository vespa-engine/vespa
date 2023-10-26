# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_net_tls_impl OBJECT
    SOURCES
    direct_buffer_bio.cpp
    iana_cipher_map.cpp
    openssl_tls_context_impl.cpp
    openssl_crypto_codec_impl.cpp
    DEPENDS
)
find_package(OpenSSL)
target_include_directories(vespalib_vespalib_net_tls_impl PUBLIC ${OPENSSL_INCLUDE_DIR})

