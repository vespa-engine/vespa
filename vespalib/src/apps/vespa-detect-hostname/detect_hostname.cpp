// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cstdio>
#include <vespa/vespalib/net/socket_address.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/size_literals.h>
#include <set>
#include <string>

using vespalib::SocketAddress;

std::set<std::string> make_ip_set() {
    std::set<std::string> result;
    for (const auto &addr: SocketAddress::get_interfaces()) {
        result.insert(addr.ip_address());
    }
    return result;
}

std::string get_hostname() {
    std::vector<char> result(4_Ki, '\0');
    gethostname(&result[0], 4000);
    return SocketAddress::normalize(&result[0]);
}

bool check(const std::string &name, const std::set<std::string> &ip_set, std::string &error_msg) {
    auto addr_list = SocketAddress::resolve(80, name.c_str());
    if (addr_list.empty()) {
        error_msg = vespalib::make_string("hostname '%s' could not be resolved", name.c_str());
        return false;
    }
    for (const SocketAddress &addr: addr_list) {
        std::string ip_addr = addr.ip_address();
        if (ip_set.count(ip_addr) == 0) {
            error_msg = vespalib::make_string("hostname '%s' resolves to ip address not owned by this host (%s)",
                                              name.c_str(), ip_addr.c_str());
            return false;
        }
    }
    return true;
}

int main(int, char **) {
    auto my_ip_set = make_ip_set();
    std::string my_hostname = get_hostname();
    std::string my_hostname_error;
    std::string localhost = "localhost";
    std::string localhost_error;
    if (check(my_hostname, my_ip_set, my_hostname_error)) {
        fprintf(stdout, "%s\n", my_hostname.c_str());
    } else if (check(localhost, my_ip_set, localhost_error)) {
        fprintf(stderr, "WARN: hostname detection failed, falling back to local hostname: %s", localhost.c_str());
        fprintf(stderr, "  WARN: canonical hostname (from gethostname/getaddrinfo): %s\n", my_hostname.c_str());
        fprintf(stderr, "  WARN: %s\n", my_hostname_error.c_str());

        fprintf(stdout, "%s\n", localhost.c_str());
    } else {
        fprintf(stderr, "FATAL: hostname detection failed\n");
        fprintf(stderr, "  INFO: canonical hostname (from gethostname/getaddrinfo): %s\n", my_hostname.c_str());
        fprintf(stderr, "  ERROR: %s\n", my_hostname_error.c_str());
        fprintf(stderr, "  INFO: falling back to local hostname: %s\n", localhost.c_str());
        fprintf(stderr, "  ERROR: %s\n", localhost_error.c_str());
        return 1;
    }
    return 0;
}
