// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "crypto_socket.h"
#include <vespa/vespalib/net/connection_auth_context.h>

namespace vespalib {

CryptoSocket::~CryptoSocket() = default;

std::unique_ptr<net::ConnectionAuthContext>
CryptoSocket::make_auth_context()
{
    return std::make_unique<net::ConnectionAuthContext>(
            net::tls::PeerCredentials(),
            net::tls::CapabilitySet::make_with_all_capabilities());
}

} // namespace vespalib
