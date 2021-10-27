// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "socket_spec.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib {

namespace {

const vespalib::string tcp_prefix("tcp/");
const vespalib::string ipc_path_prefix("ipc/file:");
const vespalib::string ipc_name_prefix("ipc/name:");
const vespalib::string fallback_host("localhost");

SocketAddress make_address(const char *node, int port, bool server) {
    if (server) {
        return SocketAddress::select_local(port, node);
    } else {
        return SocketAddress::select_remote(port, node);
    }
}

SocketAddress make_address(int port, bool server) {
    const char *node = server ? nullptr : fallback_host.c_str();
    return make_address(node, port, server);
}

} // namespace vespalib::<unnamed>

const vespalib::string SocketSpec::_empty;

SocketAddress
SocketSpec::address(bool server) const
{
    switch (_type) {
    case Type::PATH:      return SocketAddress::from_path(_node);
    case Type::NAME:      return SocketAddress::from_name(_node);
    case Type::HOST_PORT: return make_address(_node.c_str(), _port, server);
    case Type::PORT:      return make_address(_port, server);
    case Type::INVALID:   ;
    }
    return SocketAddress();
}

const SocketSpec SocketSpec::invalid;

SocketSpec::SocketSpec(const vespalib::string &spec)
    : SocketSpec()
{
    if (starts_with(spec, ipc_path_prefix)) {
        _node = spec.substr(ipc_path_prefix.size());
        _type = Type::PATH;
    } else if (starts_with(spec, ipc_name_prefix)) {
        _node = spec.substr(ipc_name_prefix.size());
        _type = Type::NAME;
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
                _node.assign(host_str, host_str_len);
                _type = Type::HOST_PORT;
            } else {
                _type = Type::PORT;
            }
        }
    }
    if ((_type != Type::PORT) && _node.empty()) {
        _type = Type::INVALID;
        _port = -1;
    }
}

vespalib::string
SocketSpec::spec() const
{
    switch (_type) {
    case Type::PATH:      return make_string("ipc/file:%s", _node.c_str());
    case Type::NAME:      return make_string("ipc/name:%s", _node.c_str());
    case Type::HOST_PORT: 
        if (_node.find(':') != _node.npos) {
            return make_string("tcp/[%s]:%d", _node.c_str(), _port);
        } else {
            return make_string("tcp/%s:%d", _node.c_str(), _port);            
        }
    case Type::PORT:      return make_string("tcp/%d", _port);
    case Type::INVALID:   ;
    }
    return "invalid";
}

SocketSpec
SocketSpec::replace_host(const vespalib::string &new_host) const
{
    if ((_type == Type::HOST_PORT) && !new_host.empty()) {
        return from_host_port(new_host, _port);
    }
    return SocketSpec();
}

const vespalib::string &
SocketSpec::host_with_fallback() const
{
    return (_type == Type::PORT) ? fallback_host : host();
}

} // namespace vespalib
