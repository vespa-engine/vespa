// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/net/tls/peer_policies.h>
#include <vespa/vespalib/net/tls/capability_set.h>

namespace vespalib::net::tls {

RequiredPeerCredential required_cn(vespalib::stringref pattern);
RequiredPeerCredential required_san_dns(vespalib::stringref pattern);
RequiredPeerCredential required_san_uri(vespalib::stringref pattern);
PeerPolicy policy_with(std::vector<RequiredPeerCredential> creds);
PeerPolicy policy_with(std::vector<RequiredPeerCredential> creds, CapabilitySet capabilities);
AuthorizedPeers authorized_peers(std::vector<PeerPolicy> peer_policies);
// Some shortcuts for valid capabilities:
Capability cap_1();
Capability cap_2();
Capability cap_3();
Capability cap_4();

}
