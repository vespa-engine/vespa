// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ipackethandler.h"
#include <mutex>
#include <condition_variable>

/**
 * This class implements a queue of packets. Being in a queue does not
 * affect the packet's internal data. This is the superclass of the
 * @ref FNET_PacketQueue. As seen by its name, this class has no
 * locking. All functionallity offered by this class is also available
 * in the subclass. However, this class may be a good lightweight
 * alternative to the heavier subclass in single threaded applications
 * or where the surrounding code handles thread-safety.
 **/
class FNET_PacketQueue_NoLock : public FNET_IPacketHandler
{
private:
    FNET_PacketQueue_NoLock(const FNET_PacketQueue_NoLock &);
    FNET_PacketQueue_NoLock &operator=(const FNET_PacketQueue_NoLock &);


protected:
#ifndef IAM_DOXYGEN
    struct _QElem
    {
        FNET_Packet  *_packet;
        FNET_Context  _context;
    protected:
        _QElem() : _packet(nullptr), _context() {}
    };
#endif // DOXYGEN

    _QElem      *_buf;
    uint32_t     _bufsize;
    uint32_t     _bufused;
    uint32_t     _in_pos;
    uint32_t     _out_pos;
    HP_RetCode   _hpRetCode; // HandlePacket return value


    /**
     * Ensure that we have enough free space on the queue. Calling this
     * method will expand the queue by calling the ExpandBuf method if
     * there is insufficient free space.
     *
     * @param needentries the number of free packet entries needed.
     **/
    void EnsureFree(uint32_t needentries = 1)
    {
        if (_bufsize < _bufused + needentries)
            ExpandBuf(needentries);
    }


    /**
     * Expand the buffer capacity of this queue.
     *
     * @param needentries the number of free packet entries needed.
     **/
    void ExpandBuf(uint32_t needentries);


public:

    /**
     * Construct a packet queue.
     *
     * @param len initial number of free packet entries. Default is 64.
     * @param hpRetCode the value that should be returned when used
     *                  as a packet handler. Default is FNET_KEEP_CHANNEL.
     **/
    FNET_PacketQueue_NoLock(uint32_t len = 64,
                            HP_RetCode hpRetCode = FNET_KEEP_CHANNEL);
    virtual ~FNET_PacketQueue_NoLock();


    /**
     * Handle incoming packet by putting it on the queue. This method
     * uses the hpRetCode value given to the constructor to decide what
     * to do with the channel delivering the packet.
     *
     * @return channel command: keep open, close or free.
     * @param packet the packet to handle.
     * @param context the packet context.
     **/
    HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context) override;


    /**
     * Queue a packet. NOTE: packet handover (caller TO invoked object).
     *
     * @param packet the packet you want to queue.
     * @param context the context for the packet.
     **/
    void QueuePacket_NoLock(FNET_Packet *packet, FNET_Context context);


    /**
     * Check if the queue is empty.
     *
     * @return true if empty, false otherwise.
     **/
    bool IsEmpty_NoLock() { return _bufused == 0; }


    /**
     * Obtain the number of packets on the queue.
     *
     * @return number of packets on the queue.
     **/
    uint32_t GetPacketCnt_NoLock() { return _bufused; }


    /**
     * Remove the first packet from the queue and return it. If the
     * queue was empty, nullptr is returned. NOTE: packet handover (invoked
     * object TO caller).
     *
     * @return first packet in queue or nullptr.
     * @param context where to store the packet context.
     **/
    FNET_Packet *DequeuePacket_NoLock(FNET_Context *context);


    /**
     * Move all packets currently in this packet queue into the queue
     * given as parameter. NOTE: caller should have exclusive access to
     * the packet queue given as parameter.
     *
     * @return number of packets flushed.
     * @param target where to flush the packets.
     **/
    uint32_t FlushPackets_NoLock(FNET_PacketQueue_NoLock *target);


    /**
     * This method is called by the destructor to discard (invoke Free
     * on) all packets in this packet queue. This method is also called
     * by the FNET_Connection::Close method in order to get rid of the
     * packets in the output queue as soon as possible.
     **/
    void DiscardPackets_NoLock();


    /**
     * Print the contents of this packet queue to stdout. Useful for
     * debugging purposes.
     **/
    void Print(uint32_t indent = 0);
};


//------------------------------------------------------------------


/**
 * This class implements a queue of packets. Being in a queue does not
 * affect the packet's internal data. This is an extension of the @ref
 * FNET_PacketQueue_NoLock class that also supports thread-safe
 * operations. The indirectly inherited packethandler callback method
 * and the print method are overridden to support thread-safe
 * behavior.
 **/
class FNET_PacketQueue : public FNET_PacketQueue_NoLock
{
private:
    FNET_PacketQueue(const FNET_PacketQueue &);
    FNET_PacketQueue &operator=(const FNET_PacketQueue &);


protected:
    std::mutex              _lock;
    std::condition_variable _cond;
    uint32_t                _waitCnt;


public:

    /**
     * Construct a packet queue.
     *
     * @param len initial number of free packet entries. Default is 64.
     * @param hpRetCode the value that should be returned when used
     *                  as a packet handler. Default is FNET_KEEP_CHANNEL.
     **/
    FNET_PacketQueue(uint32_t len = 64, HP_RetCode hpRetCode = FNET_KEEP_CHANNEL);
    ~FNET_PacketQueue();

    /**
     * Handle incoming packet by putting it on the queue. This method
     * uses the hpRetCode value given to the constructor to decide what
     * to do with the channel delivering the packet.
     *
     * @return channel command: keep open, close or free.
     * @param packet the packet to handle.
     * @param context the packet context.
     **/
    HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context) override;


    /**
     * Insert a packet into this packet queue. If the queue is too small
     * it will be extended automatically. NOTE: packet handover (caller
     * TO invoked object).
     *
     * @param packet packet you want to queue.
     * @param context the context for the packet.
     **/
    void QueuePacket(FNET_Packet *packet, FNET_Context context);


    /**
     * Obtain the first packet in this packet queue. If the queue is
     * currently empty, the calling thread will wait until a packet is
     * available on the queue. NOTE: packet handover (invoked object TO
     * caller)
     *
     * @return a packet obtained from this queue
     * @param context where to store the packet context.
     **/
    FNET_Packet *DequeuePacket(FNET_Context *context);


    /**
     * Obtain the first packet in this packet queue. If the queue is
     * currently empty, the calling thread will wait until a packet is
     * available on the queue, but for no more than 'maxwait'
     * milliseconds. NOTE: packet handover (invoked object TO caller)
     *
     * @return a packet obtained from the queue or nullptr.
     * @param maxwait maximum number of milliseconds before this
     *        method call returns.
     * @param context where to store packet context.
     **/
    FNET_Packet *DequeuePacket(uint32_t maxwait,
                               FNET_Context *context);


    /**
     * Print the contents of this packet queue to stdout. Useful for
     * debugging purposes.
     **/
    void Print(uint32_t indent = 0);
};

