// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "context.h"
#include "ipackethandler.h"

#include <memory>

class FNET_Connection;
class FNET_IPacketHandler;
/**
 * A channel object represents an endpoint in a point-to-point packet
 * based virtual connection. Clients open channels by invoking the
 * OpenChannel method on objects of the ServerInfo class. Servers need
 * to listen for incoming channels by implementing the ServerAdapter
 * interface.
 **/
class FNET_Channel {
private:
    uint32_t             _id;
    FNET_Connection*     _conn;
    FNET_IPacketHandler* _handler;
    FNET_Context         _context;

    FNET_Channel(const FNET_Channel&);
    FNET_Channel& operator=(const FNET_Channel&);

public:
    using UP = std::unique_ptr<FNET_Channel>;

    FNET_Channel(uint32_t id = FNET_NOID, FNET_Connection* conn = nullptr, FNET_IPacketHandler* handler = nullptr,
                 FNET_Context context = FNET_Context())
        : _id(id), _conn(conn), _handler(handler), _context(context) {}
    void SetID(uint32_t id) { _id = id; }
    void SetConnection(FNET_Connection* conn) { _conn = conn; }
    void SetHandler(FNET_IPacketHandler* handler) { _handler = handler; }
    void SetContext(FNET_Context context) { _context = context; }
    void prefetch() { __builtin_prefetch(&_handler, 0); }

    uint32_t             GetID() { return _id; }
    FNET_Connection*     GetConnection() { return _conn; }
    FNET_IPacketHandler* GetHandler() { return _handler; }
    FNET_Context         GetContext() { return _context; }

    /**
     * Send a packet on this channel. This operation will fail if the
     * underlying connection is closed. NOTE: packet handover (caller TO
     * invoked object).
     *
     * @return false if the connection is down, true otherwise.
     * @param packet the packet to send.
     **/
    bool Send(FNET_Packet* packet);

    /**
     * Sync with the underlying connection. When this method is invoked
     * it will block until all packets currently posted on the
     * underlying connection is encoded into the output buffer. Also,
     * the amount of data in the connection output buffer will be less
     * than the amount given by FNET_Connection::FNET_WRITE_SIZE.
     **/
    void Sync();

    /**
     * This method is called when a packet was received on this channel.
     * NOTE: packet handover (caller TO invoked object).
     *
     * @return channel command: keep open, close or free.
     * @param packet the received packet.
     **/
    FNET_IPacketHandler::HP_RetCode Receive(FNET_Packet* packet);

    /**
     * Close this channel. After a channel is closed, no more packets
     * will be delivered through the channel by the network layer. The
     * application may however still send packets on the channel.
     *
     * @return false if the channel was not open.
     **/
    bool Close();

    /**
     * Free this channel. This will free the connection reference owned
     * by this channel and also put the channel object back on the
     * object pool for re-use. This channel object may not be used after
     * this method is called.
     **/
    void Free();

    /**
     * Close and Free this channel in a single operation. This has the
     * same effect as invoking @ref Close then @ref Free, but is more
     * efficient.
     **/
    void CloseAndFree();
};
