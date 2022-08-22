# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_net_tls OBJECT
    SOURCES
    authorization_mode.cpp
    auto_reloading_tls_crypto_engine.cpp
    capability.cpp
    capability_env_config.cpp
    capability_set.cpp
    crypto_codec.cpp
    crypto_codec_adapter.cpp
    maybe_tls_crypto_engine.cpp
    maybe_tls_crypto_socket.cpp
    peer_credentials.cpp
    peer_policies.cpp
    policy_checking_certificate_verifier.cpp
    protocol_snooping.cpp
    statistics.cpp
    tls_context.cpp
    tls_crypto_engine.cpp
    tls_crypto_socket.cpp
    transport_security_options.cpp
    transport_security_options_reading.cpp
    verification_result.cpp
    DEPENDS
)
find_package(OpenSSL)
target_include_directories(vespalib_vespalib_net_tls PUBLIC ${OPENSSL_INCLUDE_DIR})

