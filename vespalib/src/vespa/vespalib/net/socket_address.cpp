// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket_address.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <sys/types.h>
#include <sys/un.h>
#include <arpa/inet.h>
#include <ifaddrs.h>
#include <netdb.h>
#include <cassert>

namespace vespalib {

namespace {

const in6_addr ipv6_wildcard = IN6ADDR_ANY_INIT;

socklen_t get_ip_addr_size(const sockaddr *addr) {
    if (addr != nullptr) {
        if (addr->sa_family == AF_INET) {
            return sizeof(sockaddr_in);
        }
        if (addr->sa_family == AF_INET6) {
            return sizeof(sockaddr_in6);
        }
    }
    return 0;
}

} // namespace vespalib::<unnamed>

bool
SocketAddress::is_wildcard() const
{
    if (is_ipv4()) {
        return (addr_in()->sin_addr.s_addr == htonl(INADDR_ANY));
    }
    if (is_ipv6()) {
        return (memcmp(&addr_in6()->sin6_addr, &ipv6_wildcard, sizeof(in6_addr)) == 0);
    }
    return false;
}

bool
SocketAddress::is_abstract() const
{
    bool result = false;
    if (is_ipc()) {
        const char *path_limit = (reinterpret_cast<const char *>(addr_un()) + _size);
        const char *pos = &addr_un()->sun_path[0];
        result = ((path_limit > pos) && (pos[0] == '\0'));
    }
    return result;
}

vespalib::string
SocketAddress::ip_address() const
{
    vespalib::string result;
    if (is_ipv4()) {
        char buf[INET_ADDRSTRLEN];
        result = inet_ntop(AF_INET, &addr_in()->sin_addr, buf, sizeof(buf));
    } else if (is_ipv6()) {
        char buf[INET6_ADDRSTRLEN];
        result = inet_ntop(AF_INET6, &addr_in6()->sin6_addr, buf, sizeof(buf));
    }
    return result;
}

vespalib::string
SocketAddress::reverse_lookup() const
{
    std::vector<char> result(4096, '\0');
    getnameinfo(addr(), _size, &result[0], 4000, nullptr, 0, NI_NAMEREQD);
    return &result[0];
}

vespalib::string
SocketAddress::path() const
{
    vespalib::string result;
    if (is_ipc() && !is_abstract()) {
        const char *path_limit = (reinterpret_cast<const char *>(addr_un()) + _size);
        const char *pos = &addr_un()->sun_path[0];
        const char *end = pos;
        while ((end < path_limit) && (*end != 0)) {
            ++end;
        }
        result.assign(pos, end - pos);
    }
    return result;
}

vespalib::string
SocketAddress::name() const
{
    vespalib::string result;
    if (is_ipc() && is_abstract()) {
        const char *path_limit = (reinterpret_cast<const char *>(addr_un()) + _size);
        const char *pos = &addr_un()->sun_path[1];
        const char *end = pos;
        while ((end < path_limit) && (*end != 0)) {
            ++end;
        }
        result.assign(pos, end - pos);
    }
    return result;
}

SocketAddress::SocketAddress(const sockaddr *addr_in, socklen_t addrlen_in)
    : _size(addrlen_in),
      _addr()
{
    memset(&_addr, 0, sizeof(_addr));
    memcpy(&_addr, addr_in, _size);
}

int
SocketAddress::port() const
{
    if (is_ipv4()) {
        return ntohs(addr_in()->sin_port);
    }
    if (is_ipv6()) {
        return ntohs(addr_in6()->sin6_port);        
    }
    return -1;
}

vespalib::string
SocketAddress::spec() const
{
    if (is_wildcard()) {
        return make_string("tcp/%d", port());
    }
    if (is_ipv4()) {
        return make_string("tcp/%s:%d", ip_address().c_str(), port());
    }
    if (is_ipv6()) {
        return make_string("tcp/[%s]:%d", ip_address().c_str(), port());
    }
    if (is_ipc()) {
        if (is_abstract()) {
            return make_string("ipc/name:%s", name().c_str());
        } else {
            return make_string("ipc/file:%s", path().c_str());
        }
    }
    return "invalid";
}

SocketHandle
SocketAddress::connect(const std::function<bool(SocketHandle&)> &tweak) const
{
    if (valid()) {
        SocketHandle handle(socket(_addr.ss_family, SOCK_STREAM, 0));
        if (handle.valid() && tweak(handle)) {
            if ((::connect(handle.get(), addr(), _size) == 0) || (errno == EINPROGRESS)) {
                return handle;
            }
        }
    }
    return SocketHandle();
}

SocketHandle
SocketAddress::listen(int backlog) const
{
    if (valid()) {
        SocketHandle handle(socket(_addr.ss_family, SOCK_STREAM, 0));
        if (handle.valid()) {
            if (is_ipv6()) {
                handle.set_ipv6_only(false);
            }
            if (port() > 0) {
                handle.set_reuse_addr(true);
            }
            if ((bind(handle.get(), addr(), _size) == 0) &&
                (::listen(handle.get(), backlog) == 0))
            {
                return handle;
            }
        }
    }
    return SocketHandle();
}

SocketAddress
SocketAddress::address_of(int sockfd)
{
    SocketAddress result;
    sockaddr *addr = reinterpret_cast<sockaddr *>(&result._addr);
    socklen_t addr_len = sizeof(result._addr);
    if (getsockname(sockfd, addr, &addr_len) == 0) {
        assert(addr_len <= sizeof(result._addr));
        result._size = addr_len;
    }
    return result;
}

SocketAddress
SocketAddress::peer_address(int sockfd)
{
    SocketAddress result;
    sockaddr *addr = reinterpret_cast<sockaddr *>(&result._addr);
    socklen_t addr_len = sizeof(result._addr);
    if (getpeername(sockfd, addr, &addr_len) == 0) {
        assert(addr_len <= sizeof(result._addr));
        result._size = addr_len;
    }
    return result;
}

std::vector<SocketAddress>
SocketAddress::resolve(int port, const char *node) {
    std::vector<SocketAddress> result;
    addrinfo hints;
    memset(&hints, 0, sizeof(addrinfo));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = 0;
    hints.ai_flags = (AI_PASSIVE | AI_NUMERICSERV | AI_ADDRCONFIG);
    vespalib::string service = make_string("%d", port);
    addrinfo *list = nullptr;
    if (getaddrinfo(node, service.c_str(), &hints, &list) == 0) {
        for (const addrinfo *info = list; info != nullptr; info = info->ai_next) {
            result.push_back(SocketAddress(info->ai_addr, info->ai_addrlen));
        }
        freeaddrinfo(list);
    }
    return result;
}

SocketAddress
SocketAddress::select_local(int port, const char *node)
{
    auto prefer_ipv6 = [](const auto &a, const auto &b) { return (!a.is_ipv6() && b.is_ipv6()); };
    return select(prefer_ipv6, port, node);
}

SocketAddress
SocketAddress::select_remote(int port, const char *node)
{
    auto prefer_ipv4 = [](const auto &a, const auto &b) { return (!a.is_ipv4() && b.is_ipv4()); };
    return select(prefer_ipv4, port, node);
}

SocketAddress
SocketAddress::from_path(const vespalib::string &path)
{
    SocketAddress result;
    sockaddr_un &addr_un = reinterpret_cast<sockaddr_un &>(result._addr);
    if (!path.empty() && (path.size() < sizeof(addr_un.sun_path))) {
        addr_un.sun_family = AF_UNIX;
        memcpy(&addr_un.sun_path[0], path.data(), path.size());        
        result._size = sizeof(sockaddr_un);
    }
    return result;
}

SocketAddress
SocketAddress::from_name(const vespalib::string &name)
{
    SocketAddress result;
    sockaddr_un &addr_un = reinterpret_cast<sockaddr_un &>(result._addr);
    if (!name.empty() && (name.size() < sizeof(addr_un.sun_path))) {
        addr_un.sun_family = AF_UNIX;
        memcpy(&addr_un.sun_path[1], name.data(), name.size());
        result._size = sizeof(sockaddr_un);
    }
    return result;
}

std::vector<SocketAddress>
SocketAddress::get_interfaces()
{
    std::vector<SocketAddress> result;
    ifaddrs *list = nullptr;
    if (getifaddrs(&list) == 0) {
        for (const ifaddrs *entry = list; entry != nullptr; entry = entry->ifa_next) {
            socklen_t size = get_ip_addr_size(entry->ifa_addr);
            if (size > 0) {
                result.push_back(SocketAddress(entry->ifa_addr, size));
            }
        }
        freeifaddrs(list);
    }
    return result;
}

vespalib::string
SocketAddress::normalize(const vespalib::string &host_name)
{
    vespalib::string result = host_name;
    addrinfo hints;
    memset(&hints, 0, sizeof(addrinfo));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = 0;
    hints.ai_flags = (AI_CANONNAME);
    addrinfo *list = nullptr;
    if (getaddrinfo(host_name.c_str(), nullptr, &hints, &list) == 0) {
        if ((list != nullptr) && (list->ai_canonname != nullptr)) {
            result = list->ai_canonname;
        }
        freeaddrinfo(list);
    }
    return result;
}

} // namespace vespalib
