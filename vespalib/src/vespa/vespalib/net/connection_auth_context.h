// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

// TODO consider moving out of tls sub-namespace
#include <vespa/vespalib/net/tls/peer_credentials.h>
#include <vespa/vespalib/net/tls/capability_set.h>

namespace vespalib::net {

class ConnectionAuthContext {
    tls::PeerCredentials _peer_credentials;
    tls::CapabilitySet   _capabilities;
public:
    ConnectionAuthContext(tls::PeerCredentials peer_credentials,
                          tls::CapabilitySet capabilities) noexcept;

    ConnectionAuthContext(const ConnectionAuthContext&);
    ConnectionAuthContext& operator=(const ConnectionAuthContext&);
    ConnectionAuthContext(ConnectionAuthContext&&) noexcept;
    ConnectionAuthContext& operator=(ConnectionAuthContext&&) noexcept;

    ~ConnectionAuthContext();

    const tls::PeerCredentials& peer_credentials() const noexcept { return _peer_credentials; }
    const tls::CapabilitySet& capabilities() const noexcept { return _capabilities; }
};

}
