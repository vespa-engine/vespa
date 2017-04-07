// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    vespalib::string _path;
    vespalib::string _host;
    int              _port;

    SocketSpec() : _path(), _host(), _port(-1) {}
    SocketSpec(const vespalib::string &path, const vespalib::string &host, int port)
        : _path(path), _host(host), _port(port) {}
    SocketAddress address(bool server) const;
public:
    explicit SocketSpec(const vespalib::string &spec);
    static SocketSpec from_path(const vespalib::string &path) {
        return SocketSpec(path, "", -1);
    }
    static SocketSpec from_host_port(const vespalib::string &host, int port) {
        return SocketSpec("", host, port);
    }
    static SocketSpec from_port(int port) {
        return SocketSpec("", "", port);
    }
    bool valid() const { return (!_path.empty() || (_port >= 0)); }
    const vespalib::string &path() const { return _path; }
    const vespalib::string &host() const { return _host; }
    int port() const { return _port; }
    SocketAddress client_address() const { return address(false); }
    SocketAddress server_address() const { return address(true); }
};

} // namespace vespalib
