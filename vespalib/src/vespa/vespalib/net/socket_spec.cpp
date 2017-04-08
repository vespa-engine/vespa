// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket_spec.h"

namespace vespalib {

namespace {

const vespalib::string tcp_prefix("tcp/");
const vespalib::string ipc_prefix("ipc/file:");

} // namespace vespalib::<unnamed>

SocketAddress
SocketSpec::address(bool server) const
{
    if (!valid()) {
        return SocketAddress();
    }
    if (!_path.empty()) {
        return SocketAddress::from_path(_path);
    }
    const char *fallback = server ? nullptr : "localhost";
    const char *node = _host.empty() ? fallback : _host.c_str();
    if (server) {
        return SocketAddress::select_local(_port, node);
    } else {
        return SocketAddress::select_remote(_port, node);
    }
}

SocketSpec::SocketSpec(const vespalib::string &spec)
    : SocketSpec()
{
    if (starts_with(spec, ipc_prefix)) {
        _path = spec.substr(ipc_prefix.size());
    } else if (starts_with(spec, tcp_prefix)) {
        bool with_host = (spec.find(':') != spec.npos);
        const char *port_str = spec.c_str() + (with_host
                                               ? (spec.rfind(':') + 1)
                                               : tcp_prefix.size());
        int port = atoi(port_str);
        if ((port > 0) || (strcmp(port_str, "0") == 0)) {
            _port = port;
            if (with_host) {
                const char *host_str = spec.c_str() + tcp_prefix.size();
                size_t host_str_len = (port_str - host_str) - 1;
                if ((host_str_len >= 2)
                    && (host_str[0] == '[')
                    && (host_str[host_str_len - 1] == ']'))
                {
                    ++host_str;
                    host_str_len -= 2;
                }
                _host.assign(host_str, host_str_len);
            }
        }
    }
}

} // namespace vespalib
