// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <vector>
#include <iosfwd>

namespace vespalib::net::tls {

struct CredentialMatchPattern {
    virtual ~CredentialMatchPattern() = default;
    [[nodiscard]] virtual bool matches(vespalib::stringref str) const = 0;

    static std::shared_ptr<const CredentialMatchPattern> create_from_glob(vespalib::stringref pattern);
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
    ~RequiredPeerCredential();

    bool operator==(const RequiredPeerCredential& rhs) const {
        // We assume (opaque) _match_pattern matches rhs._match_pattern if the pattern
        // strings they were created from are equal. This should be fully deterministic.
        return ((_field == rhs._field)
                && (_original_pattern == rhs._original_pattern));
    }

    [[nodiscard]] bool matches(vespalib::stringref str) const {
        return (_match_pattern && _match_pattern->matches(str));
    }

    Field field() const noexcept { return _field; }
    const vespalib::string& original_pattern() const noexcept { return _original_pattern; }
};

class PeerPolicy {
    // _All_ credentials must match for the policy itself to match.
    std::vector<RequiredPeerCredential> _required_peer_credentials;
public:
    PeerPolicy() = default;
    explicit PeerPolicy(std::vector<RequiredPeerCredential> required_peer_credentials_)
        : _required_peer_credentials(std::move(required_peer_credentials_))
    {}

    bool operator==(const PeerPolicy& rhs) const {
        return (_required_peer_credentials == rhs._required_peer_credentials);
    }
    const std::vector<RequiredPeerCredential>& required_peer_credentials() const noexcept {
        return _required_peer_credentials;
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

    AuthorizedPeers(const AuthorizedPeers&) = default;
    AuthorizedPeers& operator=(const AuthorizedPeers&) = default;
    AuthorizedPeers(AuthorizedPeers&&) noexcept = default;
    AuthorizedPeers& operator=(AuthorizedPeers&&) noexcept = default;

    static AuthorizedPeers allow_all_authenticated() {
        return AuthorizedPeers(true);
    }

    bool operator==(const AuthorizedPeers& rhs) const {
        return (_peer_policies == rhs._peer_policies);
    }
    [[nodiscard]] bool allows_all_authenticated() const noexcept {
        return _allow_all_if_empty;
    }
    const std::vector<PeerPolicy>& peer_policies() const noexcept { return _peer_policies; }
};

std::ostream& operator<<(std::ostream&, const RequiredPeerCredential&);
std::ostream& operator<<(std::ostream&, const PeerPolicy&);
std::ostream& operator<<(std::ostream&, const AuthorizedPeers&);

} // vespalib::net::tls
