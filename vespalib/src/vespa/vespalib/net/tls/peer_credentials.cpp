// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "peer_credentials.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace vespalib::net::tls {

PeerCredentials::PeerCredentials() = default;
PeerCredentials::PeerCredentials(const PeerCredentials&) = default;
PeerCredentials& PeerCredentials::operator=(const PeerCredentials&) = default;
PeerCredentials::PeerCredentials(PeerCredentials&&) noexcept = default;
PeerCredentials& PeerCredentials::operator=(PeerCredentials&&) noexcept = default;
PeerCredentials::~PeerCredentials() = default;

std::ostream& operator<<(std::ostream& os, const PeerCredentials& creds) {
    os << creds.to_string();
    return os;
}

namespace {
void emit_comma_separated_string_list(asciistream& os, stringref title,
                                      const std::vector<string>& strings, bool prefix_comma)
{
    if (prefix_comma) {
        os << ", ";
    }
    os << title << " [";
    for (size_t i = 0; i < strings.size(); ++i) {
        if (i != 0) {
            os << ", ";
        }
        os << '\'' << strings[i] << '\'';
    }
    os << ']';
}
}

vespalib::string PeerCredentials::to_string() const {
    asciistream os;
    os << "PeerCredentials(";
    bool emit_comma = false;
    if (!common_name.empty()) {
        os << "CN '" << common_name << "'";
        emit_comma = true;
    }
    if (!dns_sans.empty()) {
        emit_comma_separated_string_list(os, "DNS SANs", dns_sans, emit_comma);
        emit_comma = true;
    }
    if (!uri_sans.empty()) {
        emit_comma_separated_string_list(os, "URI SANs", uri_sans, emit_comma);
    }
    os << ')';
    return os.str();
}

}
