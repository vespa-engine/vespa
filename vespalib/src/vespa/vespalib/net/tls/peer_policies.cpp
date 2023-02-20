// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "peer_policies.h"
#include <vespa/vespalib/regex/regex.h>
#include <iostream>

namespace vespalib::net::tls {

namespace {

bool
is_regex_special_char(char c) noexcept {
    switch (c) {
    case '^':
    case '$':
    case '|':
    case '{':
    case '}':
    case '(':
    case ')':
    case '[':
    case ']':
    case '\\':
    case '+':
    case '.':
    case '?':
    case '*':
        return true;
    default:
        return false;
    }
}

// Important: `delimiter` MUST NOT be a character that needs escaping within a regex [charset]
template <bool SupportSingleCharMatch>
std::string
char_delimited_glob_to_regex(vespalib::stringref glob, char delimiter) {
    std::string ret = "^";
    ret.reserve(glob.size() + 2);
    // Note: we explicitly stop matching at a delimiter boundary.
    // This is to make path fragment matching less vulnerable to dirty tricks.
    const std::string wildcard_pattern    = std::string("[^") + delimiter + "]*";
    // Same applies for single chars; they should only match _within_ a delimited boundary.
    const std::string single_char_pattern = std::string("[^") + delimiter + "]";
    for (auto c : glob) {
        if (c == '*') {
            ret += wildcard_pattern;
        } else if (c == '?' && SupportSingleCharMatch) {
            ret += single_char_pattern;
        } else {
            if (is_regex_special_char(c)) {
                ret += '\\';
            }
            ret += c;
        }
    }
    ret += '$';
    return ret;
}

class RegexHostMatchPattern : public CredentialMatchPattern {
    Regex _pattern_as_regex;
    explicit RegexHostMatchPattern(std::string_view glob_pattern)
        : _pattern_as_regex(Regex::from_pattern(glob_pattern))
    {
    }
public:
    RegexHostMatchPattern(RegexHostMatchPattern&&) noexcept = default;
    ~RegexHostMatchPattern() override = default;

    RegexHostMatchPattern& operator=(RegexHostMatchPattern&&) noexcept = default;

    [[nodiscard]] static RegexHostMatchPattern from_dns_glob_pattern(vespalib::stringref glob_pattern) {
        return RegexHostMatchPattern(char_delimited_glob_to_regex<true>(glob_pattern, '.'));
    }

    [[nodiscard]] static RegexHostMatchPattern from_uri_glob_pattern(vespalib::stringref glob_pattern) {
        return RegexHostMatchPattern(char_delimited_glob_to_regex<false>(glob_pattern, '/'));
    }

    [[nodiscard]] bool matches(vespalib::stringref str) const noexcept override {
        return _pattern_as_regex.full_match(std::string_view(str.data(), str.size()));
    }
};

class ExactMatchPattern : public CredentialMatchPattern {
    vespalib::string _must_match_exactly;
public:
    explicit ExactMatchPattern(vespalib::stringref str_to_match) noexcept // vespalib::string ctors marked noexcept
        : _must_match_exactly(str_to_match)
    {
    }
    ~ExactMatchPattern() override = default;

    [[nodiscard]] bool matches(vespalib::stringref str) const noexcept override {
        return (str == _must_match_exactly);
    }
};

} // anon ns

std::shared_ptr<const CredentialMatchPattern>
CredentialMatchPattern::create_from_dns_glob(vespalib::stringref glob_pattern) {
    return std::make_shared<const RegexHostMatchPattern>(RegexHostMatchPattern::from_dns_glob_pattern(glob_pattern));
}

std::shared_ptr<const CredentialMatchPattern>
    CredentialMatchPattern::create_from_uri_glob(vespalib::stringref glob_pattern) {
    return std::make_shared<const RegexHostMatchPattern>(RegexHostMatchPattern::from_uri_glob_pattern(glob_pattern));
}

std::shared_ptr<const CredentialMatchPattern>
CredentialMatchPattern::create_exact_match(vespalib::stringref str) {
    return std::make_shared<const ExactMatchPattern>(str);
}

RequiredPeerCredential::RequiredPeerCredential(Field field, vespalib::string must_match_pattern)
    : _field(field),
      _original_pattern(std::move(must_match_pattern)),
      _match_pattern(field == Field::SAN_URI ? CredentialMatchPattern::create_from_uri_glob(_original_pattern)
                                             : CredentialMatchPattern::create_from_dns_glob(_original_pattern))
{
}

RequiredPeerCredential::RequiredPeerCredential(const RequiredPeerCredential &) = default;
RequiredPeerCredential::RequiredPeerCredential(RequiredPeerCredential &&) noexcept = default;
RequiredPeerCredential & RequiredPeerCredential::operator=(RequiredPeerCredential &&) noexcept = default;
RequiredPeerCredential::~RequiredPeerCredential() = default;

PeerPolicy::PeerPolicy() = default;

PeerPolicy::PeerPolicy(std::vector<RequiredPeerCredential> required_peer_credentials)
    : _required_peer_credentials(std::move(required_peer_credentials)),
      _granted_capabilities(CapabilitySet::make_with_all_capabilities())
{
}

PeerPolicy::PeerPolicy(std::vector<RequiredPeerCredential> required_peer_credentials,
                       CapabilitySet granted_capabilities)
    : _required_peer_credentials(std::move(required_peer_credentials)),
      _granted_capabilities(granted_capabilities)
{}

PeerPolicy::~PeerPolicy() = default;

AuthorizedPeers::AuthorizedPeers(const AuthorizedPeers&) = default;
AuthorizedPeers::AuthorizedPeers(AuthorizedPeers&&) noexcept = default;
AuthorizedPeers& AuthorizedPeers::operator=(AuthorizedPeers&&) noexcept = default;
AuthorizedPeers::~AuthorizedPeers() = default;

namespace {
template <typename Collection>
void
print_joined(std::ostream& os, const Collection& coll, const char* sep) {
    bool first = true;
    for (const auto& e : coll) {
        if (!first) {
            os << sep;
        }
        first = false;
        os << e;
    }
}

constexpr const char*
to_string(RequiredPeerCredential::Field field) noexcept {
    switch (field) {
    case RequiredPeerCredential::Field::CN:      return "CN";
    case RequiredPeerCredential::Field::SAN_DNS: return "SAN_DNS";
    case RequiredPeerCredential::Field::SAN_URI: return "SAN_URI";
    }
    abort();
}

}

std::ostream&
operator<<(std::ostream& os, const RequiredPeerCredential& cred) {
    os << "RequiredPeerCredential("
       << to_string(cred.field())
       << " matches '"
       << cred.original_pattern()
       << "')";
    return os;
}

std::ostream&
operator<<(std::ostream& os, const PeerPolicy& policy) {
    os << "PeerPolicy(";
    print_joined(os, policy.required_peer_credentials(), ", ");
    os << ", " << policy.granted_capabilities().to_string() << ")";
    return os;
}

std::ostream&
operator<<(std::ostream& os, const AuthorizedPeers& authorized){
    os << "AuthorizedPeers(";
    print_joined(os, authorized.peer_policies(), ", ");
    os << ")";
    return os;
}

} // vespalib::net::tls
