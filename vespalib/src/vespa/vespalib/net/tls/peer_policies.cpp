// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "peer_policies.h"
#include <iostream>
#include <regex>

namespace vespalib::net::tls {

namespace {

// Note: this is for basix regexp only, _not_ extended regexp
bool is_basic_regex_special_char(char c) noexcept {
    switch (c) {
        case '^':
        case '$':
        case '.':
        case '[':
        case '\\':
            return true;
        default:
            return false;
    }
}

std::string glob_to_basic_regex(vespalib::stringref glob) {
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
            if (is_basic_regex_special_char(c)) {
                ret += '\\';
            }
            ret += c;
        }
    }
    ret += '$';
    return ret;
}

class RegexHostMatchPattern : public HostGlobPattern {
    std::regex _pattern_as_regex;
public:
    explicit RegexHostMatchPattern(vespalib::stringref glob_pattern)
        : _pattern_as_regex(glob_to_basic_regex(glob_pattern), std::regex_constants::basic)
    {
    }
    ~RegexHostMatchPattern() override = default;

    bool matches(vespalib::stringref str) const override {
        return std::regex_match(str.begin(), str.end(), _pattern_as_regex);
    }
};

} // anon ns

std::shared_ptr<const HostGlobPattern> HostGlobPattern::create_from_glob(vespalib::stringref glob_pattern) {
    return std::make_shared<const RegexHostMatchPattern>(glob_pattern);
}

RequiredPeerCredential::RequiredPeerCredential(Field field, vespalib::string must_match_pattern)
    : _field(field),
      _original_pattern(std::move(must_match_pattern)),
      _match_pattern(HostGlobPattern::create_from_glob(_original_pattern))
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

std::ostream& operator<<(std::ostream& os, const AllowedPeers& allowed){
    os << "AllowedPeers(";
    print_joined(os, allowed.peer_policies(), ", ");
    os << ")";
    return os;
}

} // vespalib::net::tls
