// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include "socket_address.h"

namespace vespalib {

/**
 * High-level socket address specification.
 **/
class SocketSpec
{
private:
    enum class Type { INVALID, PATH, NAME, HOST_PORT, PORT };
    static const vespalib::string _empty;
    Type                          _type;
    vespalib::string              _node;
    int                           _port;

    SocketSpec() : _type(Type::INVALID), _node(), _port(-1) {}
    SocketSpec(Type type, const vespalib::string &node, int port)
        : _type(type), _node(node), _port(port) {}
    SocketAddress address(bool server) const;
public:
    static const SocketSpec invalid;
    explicit SocketSpec(const vespalib::string &spec);
    vespalib::string spec() const;
    SocketSpec replace_host(const vespalib::string &new_host) const;
    static SocketSpec from_path(const vespalib::string &path) {
        return SocketSpec(Type::PATH, path, -1);
    }
    static SocketSpec from_name(const vespalib::string &name) {
        return SocketSpec(Type::NAME, name, -1);
    }
    static SocketSpec from_host_port(const vespalib::string &host, int port) {
        return SocketSpec(Type::HOST_PORT, host, port);
    }
    static SocketSpec from_port(int port) {
        return SocketSpec(Type::PORT, "", port);
    }
    bool valid() const { return (_type != Type::INVALID); }
    const vespalib::string &path() const { return (_type == Type::PATH) ? _node : _empty; }
    const vespalib::string &name() const { return (_type == Type::NAME) ? _node : _empty; }
    const vespalib::string &host() const { return (_type == Type::HOST_PORT) ? _node : _empty; }
    const vespalib::string &host_with_fallback() const;
    int port() const { return _port; }
    SocketAddress client_address() const { return address(false); }
    SocketAddress server_address() const { return address(true); }
};

} // namespace vespalib
