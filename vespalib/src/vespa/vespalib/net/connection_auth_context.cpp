// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "connection_auth_context.h"

namespace vespalib::net {

ConnectionAuthContext::ConnectionAuthContext(tls::PeerCredentials peer_credentials,
                                             tls::CapabilitySet capabilities) noexcept
    : _peer_credentials(std::move(peer_credentials)),
      _capabilities(std::move(capabilities))
{
}

ConnectionAuthContext::ConnectionAuthContext(const ConnectionAuthContext&) = default;
ConnectionAuthContext& ConnectionAuthContext::operator=(const ConnectionAuthContext&) = default;
ConnectionAuthContext::ConnectionAuthContext(ConnectionAuthContext&&) noexcept = default;
ConnectionAuthContext& ConnectionAuthContext::operator=(ConnectionAuthContext&&) noexcept = default;

ConnectionAuthContext::~ConnectionAuthContext() = default;

}
