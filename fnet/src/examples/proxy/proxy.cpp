// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/transport.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/fnet/connection.h>
#include <vespa/fnet/signalshutdown.h>
#include <vespa/fnet/packet.h>
#include <vespa/fnet/iserveradapter.h>
#include <vespa/fnet/ipacketstreamer.h>
#include <vespa/fnet/channel.h>
#include <vespa/fnet/connector.h>
#include <vespa/fastos/app.h>

#include <vespa/log/log.h>
LOG_SETUP("proxy");

class RawPacket : public FNET_Packet
{
private:
    FNET_DataBuffer _data;

public:
    RawPacket() : _data() {}
    uint32_t GetPCODE() override;
    uint32_t GetLength() override;
    void Encode(FNET_DataBuffer *) override;
    bool Decode(FNET_DataBuffer *src, uint32_t len) override;
};

uint32_t
RawPacket::GetPCODE()
{
    return 0;
}

uint32_t
RawPacket::GetLength()
{
    return _data.GetDataLen();
}

void
RawPacket::Encode(FNET_DataBuffer *dst)
{
    dst->WriteBytes(_data.GetData(), _data.GetDataLen());
}

bool
RawPacket::Decode(FNET_DataBuffer *src, uint32_t len)
{
    _data.WriteBytes(src->GetData(), len);
    src->DataToDead(len);
    return true;
}


class Bridge : public FNET_IPacketHandler
{
private:
    FNET_Channel    *_client;
    FNET_Connection *_server;

    Bridge(const Bridge &);
    Bridge &operator=(const Bridge &);

public:
    Bridge() : _client(nullptr), _server(nullptr) {}

    enum packet_source {
        CLIENT = 0,
        SERVER = 1
    };

    void SetConns(FNET_Channel *client,
                  FNET_Connection *server)
    {
        _client = client;
        _server = server;
    }

    HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context) override;
};


FNET_IPacketHandler::HP_RetCode
Bridge::HandlePacket(FNET_Packet *packet, FNET_Context context)
{
    HP_RetCode ret = FNET_KEEP_CHANNEL;

    if (packet->IsChannelLostCMD()) {

        if (context._value.INT == CLIENT) {

            if (_server != nullptr) {
                LOG(info, "client connection lost");
                _server->Owner()->Close(_server);
            }
            ret = FNET_FREE_CHANNEL;
            _client = nullptr;

        } else if (context._value.INT == SERVER) {

            if (_client != nullptr) {
                LOG(info, "server connection lost");
                _client->GetConnection()->Owner()->Close(_client->GetConnection());
            }
            _server->SubRef();
            _server = nullptr;

        }

        if (_client == nullptr && _server == nullptr)
            delete this;

    } else {

        if (context._value.INT == CLIENT) {
            if (_server != nullptr)
                _server->PostPacket(packet, FNET_NOID);
            else
                packet->Free();

        } else if (context._value.INT == SERVER) {
            if (_client != nullptr)
                _client->Send(packet);
            else
                packet->Free();

        }
    }

    // The admin channel on a client connection (in this case, the
    // connection with the server) are freed when the connection
    // object is destructed. The admin channel on a server connection
    // however (in this case the channel connecting us with the
    // client) must be treated as a normal channel.

    return ret;
}


class Proxy : public FNET_IServerAdapter,
              public FNET_IPacketStreamer,
              public FastOS_Application
{
private:
    FNET_Transport _transport;

public:
    Proxy() : _transport() {}
    ~Proxy() override { }
    bool GetPacketInfo(FNET_DataBuffer *src, uint32_t *plen, uint32_t *pcode, uint32_t *chid, bool *) override;
    FNET_Packet *Decode(FNET_DataBuffer *src, uint32_t plen, uint32_t pcode, FNET_Context) override;
    void Encode(FNET_Packet *packet, uint32_t chid, FNET_DataBuffer *dst) override;
    // ---------------------------------------------
    bool InitAdminChannel(FNET_Channel *channel) override;
    bool InitChannel(FNET_Channel *, uint32_t) override;
    // ---------------------------------------------
    int Main() override;
};


bool
Proxy::GetPacketInfo(FNET_DataBuffer *src, uint32_t *plen,
                     uint32_t *pcode, uint32_t *chid, bool *)
{
    if (src->GetDataLen() == 0) {
        return false;
    }
    *pcode = 0;
    *plen = src->GetDataLen();
    *chid = FNET_NOID;
    return true;
}

FNET_Packet *
Proxy::Decode(FNET_DataBuffer *src, uint32_t plen,
              uint32_t, FNET_Context)
{
    RawPacket *packet = new RawPacket();
    packet->Decode(src, plen);
    return packet;
}

void
Proxy::Encode(FNET_Packet *packet, uint32_t chid,
              FNET_DataBuffer *dst)
{
    uint32_t pcode = packet->GetPCODE();
    uint32_t len = packet->GetLength();
    (void) pcode;
    (void) chid;
    (void) len;
    packet->Encode(dst);
}

// ---------------------------------------------

bool
Proxy::InitAdminChannel(FNET_Channel *channel)
{
    Bridge *bridge = new Bridge();
    FNET_Connection *server = _transport.Connect(_argv[2], this, bridge,
                                                 FNET_Context(Bridge::SERVER));
    if (server == nullptr) {
        channel->GetConnection()->Owner()->Close(channel->GetConnection());
        delete bridge;
        return false;
    }
    bridge->SetConns(channel, server);
    channel->SetHandler(bridge);
    channel->SetContext(FNET_Context((uint32_t)Bridge::CLIENT));
    return true;
}

bool
Proxy::InitChannel(FNET_Channel *, uint32_t)
{
    return false;
}

// ---------------------------------------------

int
Proxy::Main()
{
    FNET_SignalShutDown::hookSignals();
    if (_argc != 3) {
        fprintf(stderr, "usage: %s <listen spec> <target spec>\n", _argv[0]);
        return 1;
    }

    FNET_Connector *listener =
        _transport.Listen(_argv[1], this, this);
    if (listener != nullptr)
        listener->SubRef();

    FNET_SignalShutDown ssd(_transport);
    _transport.Main();
    return 0;
}


int
main(int argc, char **argv)
{
    Proxy myapp;
    return myapp.Entry(argc, argv);
}
