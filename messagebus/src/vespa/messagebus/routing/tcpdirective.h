// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdint.h>
#include "ihopdirective.h"

namespace mbus {

/**
 * This class represents a tcp directive within a {@link Hop}'s selector. This is a connection string used to establish
 * a direct connection to a host, bypassing service lookups through Slobrok.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 * @version $Id$
 */
class TcpDirective : public IHopDirective {
private:
    string   _host;
    uint32_t _port;
    string   _session;

public:
    /**
     * Constructs a new directive to route directly to a tcp address.
     *
     * @param host    The host name to connect to.
     * @param port    The port to connect to.
     * @param session The session to route to.
     */
    TcpDirective(const vespalib::stringref &host, uint32_t port, const vespalib::stringref &session);

    /**
     * Returns the host to connect to. This may be an ip address or a name.
     *
     * @return The host.
     */
    const string &getHost() const { return _host; }

    /**
     * Returns the port to connect to on the remove host.
     *
     * @return The port number.
     */
    uint32_t getPort() const { return _port; }

    /**
     * Returns the name of the session to route to.
     *
     * @return The session name.
     */
    const string &getSession() const { return _session; }

    virtual Type getType() const { return TYPE_TCP; }
    virtual bool matches(const IHopDirective &dir) const;
    virtual string toString() const;
    virtual string toDebugString() const;
};

} // mbus

