# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_crypto OBJECT
    SOURCES
    crypto_exception.cpp
    openssl_crypto_impl.cpp
    private_key.cpp
    random.cpp
    x509_certificate.cpp
    DEPENDS
)
find_package(OpenSSL)
target_include_directories(vespalib_vespalib_crypto PUBLIC ${OPENSSL_INCLUDE_DIR})
