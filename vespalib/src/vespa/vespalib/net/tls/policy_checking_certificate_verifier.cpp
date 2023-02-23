// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "policy_checking_certificate_verifier.h"

namespace vespalib::net::tls {

namespace {

bool matches_single_san_dns_requirement(const PeerCredentials& peer_creds, const RequiredPeerCredential& requirement) {
    for (const auto& provided_cred : peer_creds.dns_sans) {
        if (requirement.matches(provided_cred)) {
            return true;
        }
    }
    return false;
}

bool matches_single_san_uri_requirement(const PeerCredentials& peer_creds, const RequiredPeerCredential& requirement) {
    for (const auto& provided_cred : peer_creds.uri_sans) {
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
            if (!matches_single_san_dns_requirement(peer_creds, required_cred)) {
                return false;
            }
            continue;
        case RequiredPeerCredential::Field::SAN_URI:
            if (!matches_single_san_uri_requirement(peer_creds, required_cred)) {
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
    AuthorizedPeers _authorized_peers;
public:
    explicit PolicyConfiguredCertificateVerifier(AuthorizedPeers authorized_peers) noexcept;

    ~PolicyConfiguredCertificateVerifier() override;

    [[nodiscard]] VerificationResult verify(const PeerCredentials& peer_creds) const override;
};

PolicyConfiguredCertificateVerifier::PolicyConfiguredCertificateVerifier(AuthorizedPeers authorized_peers) noexcept
    : _authorized_peers(std::move(authorized_peers)) {}

PolicyConfiguredCertificateVerifier::~PolicyConfiguredCertificateVerifier() = default;

VerificationResult PolicyConfiguredCertificateVerifier::verify(const PeerCredentials& peer_creds) const {
    if (_authorized_peers.allows_all_authenticated()) {
        return VerificationResult::make_authorized_with_all_capabilities();
    }
    CapabilitySet caps;
    bool matched_any_policy = false;
    for (const auto& policy : _authorized_peers.peer_policies()) {
        if (matches_all_policy_requirements(peer_creds, policy)) {
            caps.add_all(policy.granted_capabilities());
            matched_any_policy = true;
        }
    }
    if (matched_any_policy) {
        return VerificationResult::make_authorized_with_capabilities(caps);
    } else {
        return VerificationResult::make_not_authorized();
    }
}

std::shared_ptr<CertificateVerificationCallback> create_verify_callback_from(AuthorizedPeers authorized_peers) {
    return std::make_shared<PolicyConfiguredCertificateVerifier>(std::move(authorized_peers));
}

} // vespalib::net::tls
