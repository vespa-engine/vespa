// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "peer_policy_utils.h"

namespace vespalib::net::tls {

RequiredPeerCredential required_cn(vespalib::stringref pattern) {
    return {RequiredPeerCredential::Field::CN, pattern};
}

RequiredPeerCredential required_san_dns(vespalib::stringref pattern) {
    return {RequiredPeerCredential::Field::SAN_DNS, pattern};
}

RequiredPeerCredential required_san_uri(vespalib::stringref pattern) {
    return {RequiredPeerCredential::Field::SAN_URI, pattern};
}

AssumedRoles assumed_roles(const std::vector<string>& roles) {
    // TODO fix hash_set iterator range ctor to make this a one-liner
    AssumedRoles::RoleSet role_set;
    for (const auto& role : roles) {
        role_set.insert(role);
    }
    return AssumedRoles::make_for_roles(std::move(role_set));
}

PeerPolicy policy_with(std::vector<RequiredPeerCredential> creds) {
    return PeerPolicy(std::move(creds));
}

PeerPolicy policy_with(std::vector<RequiredPeerCredential> creds, AssumedRoles roles) {
    return {std::move(creds), std::move(roles)};
}

AuthorizedPeers authorized_peers(std::vector<PeerPolicy> peer_policies) {
    return AuthorizedPeers(std::move(peer_policies));
}

}
