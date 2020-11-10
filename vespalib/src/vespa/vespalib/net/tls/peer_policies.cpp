// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "peer_policies.h"
#include <vespa/vespalib/regex/regex.h>
#include <iostream>

namespace vespalib::net::tls {

namespace {

bool is_regex_special_char(char c) noexcept {
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
        return true;
    default:
        return false;
    }
}

std::string dot_separated_glob_to_regex(vespalib::stringref glob) {
    std::string ret = "^";
    ret.reserve(glob.size() + 2);
    for (auto c : glob) {
        if (c == '*') {
            // Note: we explicitly stop matching at a dot separator boundary.
            // This is to make host name matching less vulnerable to dirty tricks.
            ret += "[^.]*";
        } else if (c == '?') {
            // Same applies for single chars; they should only match _within_ a dot boundary.
            ret += "[^.]";
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
public:
    explicit RegexHostMatchPattern(vespalib::stringref glob_pattern)
        : _pattern_as_regex(Regex::from_pattern(dot_separated_glob_to_regex(glob_pattern)))
    {
    }
    ~RegexHostMatchPattern() override = default;

    [[nodiscard]] bool matches(vespalib::stringref str) const override {
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

    [[nodiscard]] bool matches(vespalib::stringref str) const override {
        return (str == _must_match_exactly);
    }
};

} // anon ns

std::shared_ptr<const CredentialMatchPattern> CredentialMatchPattern::create_from_glob(vespalib::stringref glob_pattern) {
    return std::make_shared<const RegexHostMatchPattern>(glob_pattern);
}

std::shared_ptr<const CredentialMatchPattern> CredentialMatchPattern::create_exact_match(vespalib::stringref str) {
    return std::make_shared<const ExactMatchPattern>(str);
}

RequiredPeerCredential::RequiredPeerCredential(Field field, vespalib::string must_match_pattern)
    : _field(field),
      _original_pattern(std::move(must_match_pattern)),
      // FIXME it's not RFC 2459-compliant to use exact-matching for URIs, but that's all we currently need.
      _match_pattern(field == Field::SAN_URI ? CredentialMatchPattern::create_exact_match(_original_pattern)
                                             : CredentialMatchPattern::create_from_glob(_original_pattern))
{
}

RequiredPeerCredential::~RequiredPeerCredential() = default;

namespace {
template <typename Collection>
void print_joined(std::ostream& os, const Collection& coll, const char* sep) {
    bool first = true;
    for (const auto& e : coll) {
        if (!first) {
            os << sep;
        }
        first = false;
        os << e;
    }
}
}

std::ostream& operator<<(std::ostream& os, const RequiredPeerCredential& cred) {
    os << "RequiredPeerCredential("
       << (cred.field() == RequiredPeerCredential::Field::CN ? "CN" : "SAN_DNS")
       << " matches '"
       << cred.original_pattern()
       << "')";
    return os;
}

std::ostream& operator<<(std::ostream& os, const PeerPolicy& policy) {
    os << "PeerPolicy(";
    print_joined(os, policy.required_peer_credentials(), ", ");
    os << ")";
    return os;
}

std::ostream& operator<<(std::ostream& os, const AuthorizedPeers& authorized){
    os << "AuthorizedPeers(";
    print_joined(os, authorized.peer_policies(), ", ");
    os << ")";
    return os;
}

} // vespalib::net::tls
