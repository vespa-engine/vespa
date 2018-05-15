// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdio.h>
#include <stdlib.h>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/stllike/string.h>
#include <set>

using vespalib::SocketAddress;

std::set<vespalib::string> make_ip_set() {
    std::set<vespalib::string> result;
    for (const auto &addr: SocketAddress::get_interfaces()) {
        result.insert(addr.ip_address());
    }
    return result;
}

vespalib::string normalize(const vespalib::string &hostname) {
    vespalib::string canon_name = SocketAddress::normalize(hostname);
    if (canon_name != hostname) {
        fprintf(stderr, "warning: hostname validation: '%s' is not same as canonical hostname '%s'\n",
                hostname.c_str(), canon_name.c_str());
    }
    return canon_name;
}

void check_reverse(const vespalib::string &hostname, const SocketAddress &addr) {
    std::set<vespalib::string> seen({hostname});
    vespalib::string reverse = addr.reverse_lookup();
    for (size_t i = 0; !reverse.empty() && (i < 10); ++i) {
        if (seen.count(reverse) == 0) {
            seen.insert(reverse);
            fprintf(stderr, "warning: hostname validation: found conflicting reverse lookup: '%s' -> %s -> '%s'\n",
                    hostname.c_str(), addr.ip_address().c_str(), reverse.c_str());
        }
        reverse = addr.reverse_lookup();
    }
}

int usage(const char *self) {
    fprintf(stderr, "usage: %s <hostname>\n", self);
    return 1;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        return usage(argv[0]);
    }
    bool valid = true;
    auto my_ip_set = make_ip_set();
    vespalib::string hostname = normalize(argv[1]);
    auto addr_list = SocketAddress::resolve(80, hostname.c_str());
    if (addr_list.empty()) {
        valid = false;
        fprintf(stderr, "FATAL: hostname validation failed: '%s' could not be resolved\n",
                hostname.c_str());
    }
    for (const SocketAddress &addr: addr_list) {
        vespalib::string ip_addr = addr.ip_address();
        if (my_ip_set.count(ip_addr) == 0) {
            valid = false;
            fprintf(stderr, "FATAL: hostname validation failed: '%s' resolves to ip address not owned by this host (%s)\n",
                    hostname.c_str(), ip_addr.c_str());
        } else {
            check_reverse(hostname, addr);
        }
    }
    return valid ? 0 : 1;
}
