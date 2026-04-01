// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "packet.h"

/**
 * Packets of this type may be used to send simple control signals
 * between components in the application. Control packets only contain
 * a single unsigned integer. This integer denotes the type of control
 * packet. The effect of a control packet depends on the packet
 * context it is delivered in. Control packets are also special in
 * that the Free method is overridden to do nothing. This means that
 * control packets must be deleted explicitly, which correlates nicely
 * with their static nature (you only need one instance of each type
 * of control packet). Control packets are mainly used for FNET
 * related signals and events, but the application is free to use the
 * control packets defined by FNET for it's own purposes. It may also
 * define it's own control packets as long as the command values are
 * greater than or equal to 5000. Note that control packets may NOT be
 * sent across the network.
 **/
class FNET_ControlPacket : public FNET_Packet {
private:
    uint32_t _command;

    FNET_ControlPacket(const FNET_ControlPacket&);
    FNET_ControlPacket& operator=(const FNET_ControlPacket&);

public:
    FNET_ControlPacket(uint32_t command) : _command(command) {}

    enum {
        FNET_CMD_NOCOMMAND = 0,
        FNET_CMD_CHANNEL_LOST,
        FNET_CMD_IOC_ADD,
        FNET_CMD_IOC_ENABLE_WRITE,
        FNET_CMD_IOC_HANDSHAKE_ACT,
        FNET_CMD_IOC_CLOSE,
        FNET_CMD_DETACH_SERVER_ADAPTER_INIT,
        FNET_CMD_DETACH_SERVER_ADAPTER_FINI,
        FNET_CMD_EXECUTE,
        FNET_CMD_TIMEOUT,
        FNET_CMD_BAD_PACKET,

        FNET_CMD_LASTVALUE = FNET_CMD_BAD_PACKET
    };

    static FNET_ControlPacket ChannelLost;
    static FNET_ControlPacket IOCAdd;
    static FNET_ControlPacket IOCEnableWrite;
    static FNET_ControlPacket IOCHandshakeACT;
    static FNET_ControlPacket IOCClose;
    static FNET_ControlPacket DetachServerAdapterInit;
    static FNET_ControlPacket DetachServerAdapterFini;
    static FNET_ControlPacket Execute;
    static FNET_ControlPacket Timeout;
    static FNET_ControlPacket BadPacket;

    /**
     * This method is empty.
     **/
    void Free() override;

    /**
     * @return false
     **/
    bool IsRegularPacket() override;

    /**
     * @return true
     **/
    bool IsControlPacket() override;

    uint32_t GetCommand() override;
    bool     IsChannelLostCMD() override;
    bool     IsTimeoutCMD() override;
    bool     IsBadPacketCMD() override;

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
    bool        Decode(FNET_DataBuffer*, uint32_t) override;
    std::string Print(uint32_t indent = 0) override;
};
