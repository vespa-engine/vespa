// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "peer_policy_utils.h"

namespace vespalib::net::tls {

RequiredPeerCredential required_cn(vespalib::stringref pattern) {
    return {RequiredPeerCredential::Field::CN, pattern};
}

RequiredPeerCredential required_san_dns(vespalib::stringref pattern) {
    return {RequiredPeerCredential::Field::SAN_DNS, pattern};
}

PeerPolicy policy_with(std::vector<RequiredPeerCredential> creds) {
    return PeerPolicy(std::move(creds));
}

AllowedPeers allowed_peers(std::vector<PeerPolicy> peer_policies) {
    return AllowedPeers(std::move(peer_policies));
}

}
