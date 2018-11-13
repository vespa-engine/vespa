// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "policy_checking_certificate_verifier.h"

namespace vespalib::net::tls {

namespace {

bool matches_single_san_requirement(const PeerCredentials& peer_creds, const RequiredPeerCredential& requirement) {
    for (const auto& provided_cred : peer_creds.dns_sans) {
        if (requirement.matches(provided_cred)) {
            return true;
        }
    }
    return false;
}

bool matches_cn_requirement(const PeerCredentials& peer_creds, const RequiredPeerCredential& requirement) {
    return requirement.matches(peer_creds.common_name);
}

bool matches_all_policy_requirements(const PeerCredentials& peer_creds, const PeerPolicy& policy) {
    for (const auto& required_cred : policy.required_peer_credentials()) {
        switch (required_cred.field()) {
        case RequiredPeerCredential::Field::SAN_DNS:
            if (!matches_single_san_requirement(peer_creds, required_cred)) {
                return false;
            }
            continue;
        case RequiredPeerCredential::Field::CN:
            if (!matches_cn_requirement(peer_creds, required_cred)) {
                return false;
            }
            continue;
        }
        abort();
    }
    return true;
}

} // anon ns

class PolicyConfiguredCertificateVerifier : public CertificateVerificationCallback {
    AllowedPeers _allowed_peers;
public:
    explicit PolicyConfiguredCertificateVerifier(AllowedPeers allowed_peers);

    ~PolicyConfiguredCertificateVerifier() override;

    bool verify(const PeerCredentials& peer_creds) const override;
};

PolicyConfiguredCertificateVerifier::PolicyConfiguredCertificateVerifier(AllowedPeers allowed_peers)
    : _allowed_peers(std::move(allowed_peers)) {}

PolicyConfiguredCertificateVerifier::~PolicyConfiguredCertificateVerifier() = default;

bool PolicyConfiguredCertificateVerifier::verify(const PeerCredentials& peer_creds) const {
    if (_allowed_peers.allows_all_authenticated()) {
        return true;
    }
    for (const auto& policy : _allowed_peers.peer_policies()) {
        if (matches_all_policy_requirements(peer_creds, policy)) {
            return true;
        }
    }
    return false;
}

std::shared_ptr<CertificateVerificationCallback> create_verify_callback_from(AllowedPeers allowed_peers) {
    return std::make_shared<PolicyConfiguredCertificateVerifier>(std::move(allowed_peers));
}

} // vespalib::net::tls
