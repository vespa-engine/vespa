// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packetqueue.h"
#include "packet.h"
#include <vespa/fastos/time.h>
#include <cassert>
#include <chrono>

void
FNET_PacketQueue_NoLock::ExpandBuf(uint32_t needentries)
{
    uint32_t oldsize = _bufsize;
    if (_bufsize < 8)
        _bufsize = 8;
    while (_bufsize < _bufused + needentries)
        _bufsize *= 2;
    _QElem *newbuf = static_cast<_QElem *>(malloc(sizeof(_QElem) * _bufsize));
    assert(newbuf != nullptr);
    if (_bufused == 0) {          // EMPTY
        // BUFFER: |....................|
        // USED:   |....................|

    } else if (_in_pos > _out_pos) { // NON-WRAPPED
        // BUFFER: |....................|
        // USED:   |....############....|
        //            rOfs   rLen

        uint32_t rOfs = _out_pos;
        uint32_t rLen = (_in_pos - _out_pos);
//TODO Rewrite to pure C++
#pragma GCC diagnostic push
#if __GNUC__ >= 8
#pragma GCC diagnostic ignored "-Wclass-memaccess"
#endif
        memcpy(newbuf + rOfs, _buf + rOfs, rLen * sizeof(_QElem));
#pragma GCC diagnostic pop
    } else {                      // WRAPPED
        // BUFFER: |....................|
        // USED:   |######........######|
        //          r1Len          r2Len

        uint32_t r1Len = _in_pos;
        uint32_t r2Len = (oldsize - _out_pos);
#pragma GCC diagnostic push
#if __GNUC__ >= 8
#pragma GCC diagnostic ignored "-Wclass-memaccess"
#endif
        memcpy(newbuf, _buf, r1Len * sizeof(_QElem));
        memcpy(newbuf + _bufsize - r2Len, _buf + oldsize - r2Len, r2Len * sizeof(_QElem));
#pragma GCC diagnostic pop
        _out_pos += _bufsize - oldsize;
    }
    free(_buf);
    _buf = newbuf;
}


FNET_PacketQueue_NoLock::FNET_PacketQueue_NoLock(uint32_t len,
                                                 HP_RetCode hpRetCode)
    : _buf(nullptr),
      _bufsize(len),
      _bufused(0),
      _in_pos(0),
      _out_pos(0),
      _hpRetCode(hpRetCode)
{
    _buf = static_cast<_QElem *>(malloc(sizeof(_QElem) * len));
    assert(_buf != nullptr);
}


FNET_PacketQueue_NoLock::~FNET_PacketQueue_NoLock()
{
    DiscardPackets_NoLock();
    free(_buf);
}


FNET_IPacketHandler::HP_RetCode
FNET_PacketQueue_NoLock::HandlePacket(FNET_Packet  *packet,
                                      FNET_Context  context)
{
    QueuePacket_NoLock(packet, context);
    return _hpRetCode;
}


void
FNET_PacketQueue_NoLock::QueuePacket_NoLock(FNET_Packet *packet,
                                            FNET_Context context)
{
    if (packet == nullptr)
        return;
    EnsureFree();
    _buf[_in_pos]._packet = packet;
    _buf[_in_pos]._context = context;
    if (++_in_pos == _bufsize)
        _in_pos = 0;                 // wrap around.
    _bufused++;
}


FNET_Packet*
FNET_PacketQueue_NoLock::DequeuePacket_NoLock(FNET_Context *context)
{
    assert(context != nullptr);
    FNET_Packet *packet = nullptr;
    if (_bufused > 0) {
        packet = _buf[_out_pos]._packet;
        __builtin_prefetch(packet, 0);
        *context = _buf[_out_pos]._context;
        if (++_out_pos == _bufsize)
            _out_pos = 0;              // wrap around
        _bufused--;
    }
    return packet;
}


uint32_t
FNET_PacketQueue_NoLock::FlushPackets_NoLock(FNET_PacketQueue_NoLock *target)
{
    uint32_t cnt = _bufused;

    target->EnsureFree(cnt);
    for (; _bufused > 0; _bufused--, target->_bufused++) {
        target->_buf[target->_in_pos]._packet  = _buf[_out_pos]._packet;
        target->_buf[target->_in_pos]._context = _buf[_out_pos]._context;

        if (++target->_in_pos == target->_bufsize)
            target->_in_pos = 0;         // wrap around.
        if (++_out_pos == _bufsize)
            _out_pos = 0;                // wrap around.
    }
    assert(_out_pos == _in_pos);

    return cnt;
}


void
FNET_PacketQueue_NoLock::DiscardPackets_NoLock()
{
    for (; _bufused > 0; _bufused--) {
        _buf[_out_pos]._packet->Free();   // discard packet
        if (++_out_pos == _bufsize)
            _out_pos = 0;                   // wrap around
    }
    assert(_out_pos == _in_pos);
}


void
FNET_PacketQueue_NoLock::Print(uint32_t indent)
{
    uint32_t i   = _out_pos;
    uint32_t cnt = _bufused;

    printf("%*sFNET_PacketQueue_NoLock {\n", indent, "");
    printf("%*s  bufsize : %d\n", indent, "", _bufsize);
    printf("%*s  bufused : %d\n", indent, "", _bufused);
    printf("%*s  in_pos  : %d\n", indent, "", _in_pos);
    printf("%*s  out_pos : %d\n", indent, "", _out_pos);
    for (; cnt > 0; i++, cnt--) {
        if (i == _bufsize)
            i = 0;           // wrap around
        _buf[i]._packet->Print(indent + 2);
        _buf[i]._context.Print(indent + 2);
    }
    printf("%*s}\n", indent, "");
}


//------------------------------------------------------------------


FNET_PacketQueue::FNET_PacketQueue(uint32_t len,
                                   HP_RetCode hpRetCode)
    : FNET_PacketQueue_NoLock(len, hpRetCode),
      _lock(),
      _cond(),
      _waitCnt(0)
{
}


FNET_PacketQueue::~FNET_PacketQueue()
{
}


FNET_IPacketHandler::HP_RetCode
FNET_PacketQueue::HandlePacket(FNET_Packet  *packet,
                               FNET_Context  context)
{
    QueuePacket(packet, context);
    return _hpRetCode;
}


void
FNET_PacketQueue::QueuePacket(FNET_Packet *packet, FNET_Context context)
{
    assert(packet != nullptr);
    std::lock_guard<std::mutex> guard(_lock);
    EnsureFree();
    _buf[_in_pos]._packet = packet;  // insert packet ref.
    _buf[_in_pos]._context = context;
    if (++_in_pos == _bufsize)
        _in_pos = 0;                   // wrap around.
    _bufused++;
    if (_waitCnt >= _bufused) {        // signal waiting thread(s)
        _cond.notify_one();
    }
}


FNET_Packet*
FNET_PacketQueue::DequeuePacket(FNET_Context *context)
{
    FNET_Packet *packet = nullptr;
    std::unique_lock<std::mutex> guard(_lock);
    _waitCnt++;
    while (_bufused == 0) {
        _cond.wait(guard);
    }
    _waitCnt--;
    packet = _buf[_out_pos]._packet;
    *context = _buf[_out_pos]._context;
    if (++_out_pos == _bufsize)
        _out_pos = 0;                   // wrap around
    _bufused--;
    return packet;
}


FNET_Packet*
FNET_PacketQueue::DequeuePacket(uint32_t maxwait, FNET_Context *context)
{
    FNET_Packet *packet = nullptr;
    FastOS_Time  startTime;
    int          waitTime;

    if (maxwait > 0)
        startTime.SetNow();
    std::unique_lock<std::mutex> guard(_lock);
    if (maxwait > 0) {
        bool timeout = false;

        _waitCnt++;
        while ((_bufused == 0) && !timeout && (waitTime = (int)(maxwait - startTime.MilliSecsToNow())) > 0) {
            timeout = _cond.wait_for(guard, std::chrono::milliseconds(waitTime)) == std::cv_status::timeout;
        }
        _waitCnt--;
    }
    if (_bufused > 0) {
        packet = _buf[_out_pos]._packet;
        *context = _buf[_out_pos]._context;
        if (++_out_pos == _bufsize)
            _out_pos = 0;                   // wrap around
        _bufused--;
    }
    return packet;
}


void
FNET_PacketQueue::Print(uint32_t indent)
{
    std::lock_guard<std::mutex> guard(_lock);
    uint32_t i   = _out_pos;
    uint32_t cnt = _bufused;

    printf("%*sFNET_PacketQueue {\n", indent, "");
    printf("%*s  bufsize : %d\n", indent, "", _bufsize);
    printf("%*s  bufused : %d\n", indent, "", _bufused);
    printf("%*s  in_pos  : %d\n", indent, "", _in_pos);
    printf("%*s  out_pos : %d\n", indent, "", _out_pos);
    printf("%*s  waitCnt : %d\n", indent, "", _waitCnt);
    for (; cnt > 0; i++, cnt--) {
        if (i == _bufsize)
            i = 0;           // wrap around
        _buf[i]._packet->Print(indent + 2);
        _buf[i]._context.Print(indent + 2);
    }
    printf("%*s}\n", indent, "");
}
