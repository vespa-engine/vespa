// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iocomponent.h"

#include <vespa/vespalib/net/server_socket.h>

class FNET_IPacketStreamer;
class FNET_IServerAdapter;

/**
 * Class used to listen for incoming connections on a single TCP/IP
 * port.
 **/
class FNET_Connector : public FNET_IOComponent {
private:
    FNET_IPacketStreamer*  _streamer;
    FNET_IServerAdapter*   _serverAdapter;
    vespalib::ServerSocket _server_socket;
    uint32_t               _cached_port;

    FNET_Connector(const FNET_Connector&);
    FNET_Connector& operator=(const FNET_Connector&);

public:
    /**
     * Construct a connector.
     *
     * @param owner the TransportThread object serving this connector
     * @param streamer custom packet streamer
     * @param serverAdapter object for custom channel creation
     * @param spec listen spec for this connector
     * @param server_socket the underlying server socket
     **/
    FNET_Connector(FNET_TransportThread* owner, FNET_IPacketStreamer* streamer, FNET_IServerAdapter* serverAdapter,
                   const char* spec, vespalib::ServerSocket server_socket);

    /**
     * Obtain the port number of the underlying server socket.
     *
     * @return port number
     **/
    uint32_t GetPortNumber() const;

    FNET_IServerAdapter* server_adapter() override;

    /**
     * Close this connector. This method must be called in the transport
     * thread in order to avoid race conditions related to socket event
     * registration, deregistration and triggering.
     **/
    void Close() override;

    /**
     * Called by the transport layer when a read event has occurred. If
     * an incoming connection could be accepted, an event is posted on
     * the transport thread event queue.
     *
     * @return false if connector is broken, true otherwise.
     **/
    bool HandleReadEvent() override;

    /**
     * Called by the transport layer when a write event has
     * occurred. Note that this should never happen during normal
     * operation. This method simply ignores the event, disables further
     * write events and returns true.
     *
     * @return true.
     **/
    bool HandleWriteEvent() override;
};
