// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iocomponent.h"

class FastOS_ServerSocket;
class FNET_IPacketStreamer;
class FNET_IServerAdapter;
class FastOS_SocketFactory;

/**
 * Class used to listen for incoming connections on a single TCP/IP
 * port.
 **/
class FNET_Connector : public FNET_IOComponent
{
private:
    FNET_IPacketStreamer  *_streamer;
    FNET_IServerAdapter   *_serverAdapter;
    FastOS_ServerSocket   *_serverSocket;
    bool                   _strict;

    FNET_Connector(const FNET_Connector &);
    FNET_Connector &operator=(const FNET_Connector &);

public:
    /**
     * Construct a connector.
     *
     * @param owner the TransportThread object serving this connector
     * @param streamer custom packet streamer
     * @param serverAdapter object for custom channel creation
     * @param spec listen spec for this connector
     * @param port the port to listen on
     * @param backlog accept queue length
     * @param factory custom socket factory
     * @param strictBindHostName bind strict to given hostname
     **/
    FNET_Connector(FNET_TransportThread *owner,
                   FNET_IPacketStreamer *streamer,
                   FNET_IServerAdapter *serverAdapter,
                   const char *spec,
                   int port, int backlog = 500,
                   FastOS_SocketFactory *factory = nullptr,
                   const char *strictBindHostName = nullptr);
    ~FNET_Connector();


    /**
     * Obtain the port number of the underlying server socket.
     *
     * @return port number
     **/
    uint32_t GetPortNumber() const;

    /**
     * Try to create a listening server socket at the port number
     * specified in the constructor. The socket is set to
     * non-blocking.
     *
     * @return true on success, false on fail
     **/
    bool Init();

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

