// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "socket_handle.h"
#include <vector>
#include <sys/socket.h>
#include <functional>

struct sockaddr_in;
struct sockaddr_in6;
struct sockaddr_un;

namespace vespalib {

/**
 * Wrapper class for low-level TCP/IP and IPC socket addresses.
 **/
class SocketAddress
{
private:
    socklen_t        _size;
    sockaddr_storage _addr;

    const sockaddr *addr() const { return reinterpret_cast<const sockaddr *>(&_addr); }
    const sockaddr_in *addr_in() const { return reinterpret_cast<const sockaddr_in *>(&_addr); }
    const sockaddr_in6 *addr_in6() const { return reinterpret_cast<const sockaddr_in6 *>(&_addr); }
    const sockaddr_un *addr_un() const { return reinterpret_cast<const sockaddr_un *>(&_addr); }
    SocketAddress(const sockaddr *addr_in, socklen_t addrlen_in);
public:
    SocketAddress() { memset(this, 0, sizeof(SocketAddress)); }
    SocketAddress(const SocketAddress &rhs) { memcpy(this, &rhs, sizeof(SocketAddress)); }
    SocketAddress &operator=(const SocketAddress &rhs) {
        memcpy(this, &rhs, sizeof(SocketAddress));
        return *this;
    }
    bool valid() const { return (_size >= sizeof(sa_family_t)); }
    bool is_ipv4() const { return (valid() && (_addr.ss_family == AF_INET)); }
    bool is_ipv6() const { return (valid() && (_addr.ss_family == AF_INET6)); }
    bool is_ipc() const { return (valid() && (_addr.ss_family == AF_UNIX)); }
    bool is_wildcard() const;
    bool is_abstract() const;
    int port() const;
    vespalib::string ip_address() const;
    vespalib::string reverse_lookup() const;
    vespalib::string path() const;
    vespalib::string name() const;
    vespalib::string spec() const;
    SocketHandle connect(const std::function<bool(SocketHandle&)> &tweak) const;
    SocketHandle connect() const { return connect([](SocketHandle&){ return true; }); }
    SocketHandle connect_async() const {
        return connect([](SocketHandle &handle){ return handle.set_blocking(false); });
    }
    SocketHandle listen(int backlog = 500) const;
    static SocketAddress address_of(int sockfd);
    static SocketAddress peer_address(int sockfd);
    static std::vector<SocketAddress> resolve(int port, const char *node = nullptr);
    static SocketAddress select_local(int port, const char *node = nullptr);
    static SocketAddress select_remote(int port, const char *node = nullptr);
    template <typename SELECTOR>
    static SocketAddress select(const SELECTOR &replace, int port, const char *node = nullptr) {
        auto list = resolve(port, node);
        if (!list.empty()) {
            size_t best = 0;
            for (size_t i = 1; i < list.size(); ++i) {
                if (replace(list[best], list[i])) {
                    best = i;
                }
            }
            return list[best];
        }
        return SocketAddress();
    }
    static SocketAddress from_path(const vespalib::string &path);
    static SocketAddress from_name(const vespalib::string &name);
    static std::vector<SocketAddress> get_interfaces();
    static vespalib::string normalize(const vespalib::string &host_name);
};

} // namespace vespalib
