// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
class FNET_DummyPacket : public FNET_Packet
{
public:
    /**
     * Empty constructor.
     **/
    FNET_DummyPacket();

    /**
     * @return false
     **/
    virtual bool IsRegularPacket();

    /**
     * @return false
     **/
    virtual bool IsControlPacket();

    /**
     * @return FNET_NOID
     **/
    virtual uint32_t GetPCODE();

    /**
     * @return 0
     **/
    virtual uint32_t GetLength();

    /**
     * This method should never be called and will abort the program.
     **/
    virtual void Encode(FNET_DataBuffer *);

    /**
     * This method should never be called and will abort the program.
     **/
    virtual bool Decode(FNET_DataBuffer *, uint32_t);

    /**
     * Identify as dummy packet.
     **/
    virtual vespalib::string Print(uint32_t indent = 0);
};

