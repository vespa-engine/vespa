// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "peer_credentials.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <iostream>
#include <sstream>

namespace vespalib::net::tls {

PeerCredentials::PeerCredentials() = default;
PeerCredentials::~PeerCredentials() = default;

std::ostream& operator<<(std::ostream& os, const PeerCredentials& creds) {
    os << to_string(creds);
    return os;
}

vespalib::string to_string(const PeerCredentials& creds) {
    vespalib::asciistream os;
    os << "PeerCredentials(CN '" << creds.common_name
       << "', DNS SANs [";
    for (size_t i = 0; i < creds.dns_sans.size(); ++i) {
        if (i != 0) {
            os << ", ";
        }
        os << '\'' << creds.dns_sans[i] << '\'';
    }
    os << "])";
    return os.str();
}

}
