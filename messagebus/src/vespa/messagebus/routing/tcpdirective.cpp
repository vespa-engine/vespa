// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tcpdirective.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace mbus {

TcpDirective::TcpDirective(vespalib::stringref host, uint32_t port, vespalib::stringref session) :
    _host(host),
    _port(port),
    _session(session)
{
    // empty
}

TcpDirective::~TcpDirective() = default;

bool
TcpDirective::matches(const IHopDirective &dir) const
{
    if (dir.getType() != TYPE_TCP) {
        return false;
    }
    const TcpDirective &rhs = static_cast<const TcpDirective&>(dir);
    return _host == rhs._host && _port == rhs._port && _session == rhs._session;
}

string
TcpDirective::toString() const
{
    vespalib::asciistream os;
    os << "tcp/" << _host << ':' << _port << '/' << _session;
    return os.str();
}

string
TcpDirective::toDebugString() const
{
    vespalib::asciistream os;
    os << "TcpDirective(host = '" << _host << "', port = " << _port << ", session = '" << _session << "')";
    return os.str();
}

} // mbus
