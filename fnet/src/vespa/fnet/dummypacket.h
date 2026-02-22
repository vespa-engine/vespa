// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "packet.h"

/**
 * A dummy packet is neither a regular packet nor a control
 * packet. The idea with this class is that you subclass it to make
 * packets that perform certain tasks when they are freed. The default
 * Free method simply deletes the packet object, so you may chose to
 * override either the Free method or the destructor, depending on the
 * intended lifetime of the packet.
 **/
class FNET_DummyPacket : public FNET_Packet {
public:
    /**
     * Empty constructor.
     **/
    FNET_DummyPacket();

    /**
     * @return false
     **/
    bool IsRegularPacket() override;

    /**
     * @return false
     **/
    bool IsControlPacket() override;

    /**
     * @return FNET_NOID
     **/
    uint32_t GetPCODE() override;

    /**
     * @return 0
     **/
    uint32_t GetLength() override;

    /**
     * This method should never be called and will abort the program.
     **/
    void Encode(FNET_DataBuffer*) override;

    /**
     * This method should never be called and will abort the program.
     **/
    bool Decode(FNET_DataBuffer*, uint32_t) override;

    /**
     * Identify as dummy packet.
     **/
    std::string Print(uint32_t indent = 0) override;
};
