// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "authority.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

namespace {

int default_port(bool use_https) { return use_https ? 443 : 80; }

}

vespalib::SocketSpec make_sni_spec(const std::string &authority, const char *hostname, int port, bool use_https) {
    if (authority.empty()) {
        return vespalib::SocketSpec::from_host_port(hostname, port);
    }
    auto split = authority.rfind('@');
    std::string spec_str = (split == std::string::npos) ? authority : authority.substr(split + 1);
    auto a = spec_str.rfind(':');
    auto b = spec_str.rfind(']');
    bool has_port = (a != std::string::npos) && ((b == std::string::npos) || (a > b));
    if (has_port) {
        spec_str = "tcp/" + spec_str;
    } else {
        spec_str = vespalib::make_string("tcp/%s:%d", spec_str.c_str(), default_port(use_https));
    }
    // use SocketSpec parser to ensure ipv6 addresses are dequoted
    return vespalib::SocketSpec(spec_str);
}

std::string make_host_header_value(const vespalib::SocketSpec &sni_spec, bool use_https) {
    if (sni_spec.host().empty()) {
        return "";
    }
    if (sni_spec.port() == default_port(use_https)) {
        return sni_spec.host();
    }
    // use SocketSpec formatter to ensure ipv6 addresses are quoted
    std::string spec_str = sni_spec.spec();
    assert(spec_str.find("tcp/") == 0);
    return spec_str.substr(4);
}
