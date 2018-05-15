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

vespalib::string get_hostname() {
    std::vector<char> result(4096, '\0');
    gethostname(&result[0], 4000);
    return SocketAddress::normalize(&result[0]);
}

bool check(const vespalib::string &name, const std::set<vespalib::string> &ip_set) {
    auto addr_list = SocketAddress::resolve(80, name.c_str());
    if (addr_list.empty()) {
        return false;
    }
    for (const SocketAddress &addr: addr_list) {
        vespalib::string ip_addr = addr.ip_address();
        if (ip_set.count(ip_addr) == 0) {
            return false;
        }
    }
    return true;
}

int main(int, char **) {
    auto my_ip_set = make_ip_set();
    std::vector<vespalib::string> list({get_hostname(), "localhost", "127.0.0.1", "::1"});
    for (const vespalib::string &name: list) {
        if (check(name, my_ip_set)) {
            fprintf(stdout, "%s\n", name.c_str());
            return 0;
        }
    }
    fprintf(stderr, "FATAL: hostname detection failed\n");
    // XXX we should explain why it failed
    return 1;
}
