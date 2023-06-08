// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "capability_set.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <vector>
#include <iosfwd>

namespace vespalib::net::tls {

struct CredentialMatchPattern {
    virtual ~CredentialMatchPattern() = default;
    [[nodiscard]] virtual bool matches(vespalib::stringref str) const noexcept = 0;

    static std::shared_ptr<const CredentialMatchPattern> create_from_dns_glob(vespalib::stringref glob_pattern);
    static std::shared_ptr<const CredentialMatchPattern> create_from_uri_glob(vespalib::stringref glob_pattern);
    static std::shared_ptr<const CredentialMatchPattern> create_exact_match(vespalib::stringref pattern);
};

class RequiredPeerCredential {
public:
    enum class Field {
        CN, SAN_DNS, SAN_URI
    };
private:
    Field _field = Field::SAN_DNS;
    vespalib::string _original_pattern;
    std::shared_ptr<const CredentialMatchPattern> _match_pattern;
public:
    RequiredPeerCredential() = default;
    RequiredPeerCredential(Field field, vespalib::string must_match_pattern);
    RequiredPeerCredential(const RequiredPeerCredential &) noexcept;
    RequiredPeerCredential & operator=(const RequiredPeerCredential &) = delete;
    RequiredPeerCredential(RequiredPeerCredential &&) noexcept;
    RequiredPeerCredential & operator=(RequiredPeerCredential &&) noexcept;
    ~RequiredPeerCredential();

    bool operator==(const RequiredPeerCredential& rhs) const {
        // We assume (opaque) _match_pattern matches rhs._match_pattern if the pattern
        // strings they were created from are equal. This should be fully deterministic.
        return ((_field == rhs._field)
                && (_original_pattern == rhs._original_pattern));
    }

    [[nodiscard]] bool matches(vespalib::stringref str) const noexcept {
        return (_match_pattern && _match_pattern->matches(str));
    }

    [[nodiscard]] Field field() const noexcept { return _field; }
    [[nodiscard]] const vespalib::string& original_pattern() const noexcept { return _original_pattern; }
};

class PeerPolicy {
    // _All_ credentials must match for the policy itself to match.
    std::vector<RequiredPeerCredential> _required_peer_credentials;
    CapabilitySet                       _granted_capabilities;
public:
    PeerPolicy();
    // This policy is created with a full capability set, i.e. unrestricted access.
    explicit PeerPolicy(std::vector<RequiredPeerCredential> required_peer_credentials);

    PeerPolicy(std::vector<RequiredPeerCredential> required_peer_credentials,
               CapabilitySet granted_capabilities);

    ~PeerPolicy();

    bool operator==(const PeerPolicy& rhs) const noexcept {
        return ((_required_peer_credentials == rhs._required_peer_credentials) &&
                (_granted_capabilities == rhs._granted_capabilities));
    }
    [[nodiscard]] const std::vector<RequiredPeerCredential>& required_peer_credentials() const noexcept {
        return _required_peer_credentials;
    }
    [[nodiscard]] const CapabilitySet& granted_capabilities() const noexcept {
        return _granted_capabilities;
    }
};

class AuthorizedPeers {
    // A peer will be authorized iff it matches _one or more_ policies.
    std::vector<PeerPolicy> _peer_policies;
    bool _allow_all_if_empty;

    explicit AuthorizedPeers(bool allow_all_if_empty)
        : _peer_policies(),
          _allow_all_if_empty(allow_all_if_empty)
    {}
public:
    AuthorizedPeers() : _peer_policies(), _allow_all_if_empty(false) {}
    explicit AuthorizedPeers(std::vector<PeerPolicy> peer_policies_)
        : _peer_policies(std::move(peer_policies_)),
          _allow_all_if_empty(false)
    {}

    AuthorizedPeers(const AuthorizedPeers&);
    AuthorizedPeers& operator=(const AuthorizedPeers&) = delete;
    AuthorizedPeers(AuthorizedPeers&&) noexcept;
    AuthorizedPeers& operator=(AuthorizedPeers&&) noexcept;
    ~AuthorizedPeers();

    static AuthorizedPeers allow_all_authenticated() {
        return AuthorizedPeers(true);
    }

    bool operator==(const AuthorizedPeers& rhs) const {
        return (_peer_policies == rhs._peer_policies);
    }
    [[nodiscard]] bool allows_all_authenticated() const noexcept {
        return _allow_all_if_empty;
    }
    [[nodiscard]] const std::vector<PeerPolicy>& peer_policies() const noexcept { return _peer_policies; }
};

std::ostream& operator<<(std::ostream&, const RequiredPeerCredential&);
std::ostream& operator<<(std::ostream&, const PeerPolicy&);
std::ostream& operator<<(std::ostream&, const AuthorizedPeers&);

} // vespalib::net::tls
