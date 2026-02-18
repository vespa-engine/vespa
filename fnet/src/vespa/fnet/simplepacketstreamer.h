// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ipacketstreamer.h"

class FNET_IPacketFactory;

/**
 * This is a convenience class. Large applications may want to
 * implement the functionality offered by this class themselves to
 * obtain better control. A simple but useful implementation of the
 * packet streamer interface. Packets are transmitted with a packet
 * header containing packet length, packet code and channel id. This
 * gives us a total packet header length of 12 bytes. In order to use
 * this packet streamer you must supply an object implementing the
 * packet factory interface to the constructor. The EnsureFree method
 * on the target databuffer is used to ensure that there is enough
 * free space for packets to be encoded. This means that when the
 * packet Encode method is invoked the databuffer has at least as much
 * free space as reported by the packet Length method. After each
 * packet encode/decode the AssertValid databuffer method is run to
 * check for illegal reads/writes.
 **/
class FNET_SimplePacketStreamer : public FNET_IPacketStreamer {
private:
    FNET_IPacketFactory* _factory;

    FNET_SimplePacketStreamer(const FNET_SimplePacketStreamer&);
    FNET_SimplePacketStreamer& operator=(const FNET_SimplePacketStreamer&);

public:
    FNET_SimplePacketStreamer(FNET_IPacketFactory* factory);
    ~FNET_SimplePacketStreamer();

    bool GetPacketInfo(FNET_DataBuffer* src, uint32_t* plen, uint32_t* pcode, uint32_t* chid, bool* broken) override;
    FNET_Packet* Decode(FNET_DataBuffer* src, uint32_t plen, uint32_t pcode, FNET_Context context) override;
    void         Encode(FNET_Packet* packet, uint32_t chid, FNET_DataBuffer* dst) override;
};
