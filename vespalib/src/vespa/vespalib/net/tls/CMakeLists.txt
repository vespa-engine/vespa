# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_net_tls OBJECT
    SOURCES
    crypto_codec.cpp
    crypto_exception.cpp
    tls_context.cpp
    transport_security_options.cpp
    DEPENDS
)
vespa_add_target_package_dependency(vespalib_vespalib_net_tls OpenSSL)

