// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/channel.h>
#include <vespa/fnet/connector.h>
#include <vespa/fnet/iserveradapter.h>
#include <vespa/fnet/signalshutdown.h>
#include <vespa/fnet/simplepacketstreamer.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/signalhandler.h>

#include <examples/ping/packets.h>

#include <vespa/log/log.h>
LOG_SETUP("pingserver");

class PingServer : public FNET_IServerAdapter, public FNET_IPacketHandler {
public:
    bool InitChannel(FNET_Channel* channel, uint32_t) override {
        channel->SetContext(FNET_Context(channel));
        channel->SetHandler(this);
        return true;
    }

    HP_RetCode HandlePacket(FNET_Packet* packet, FNET_Context context) override {
        if (packet->GetPCODE() == PCODE_PING_REQUEST) {
            fprintf(stderr, "Got ping request, sending ping reply\n");
            context._value.CHANNEL->Send(new PingReply());
        }
        packet->Free();
        return FNET_FREE_CHANNEL;
    }

    int main(int argc, char** argv);
};

int PingServer::main(int argc, char** argv) {
    FNET_SignalShutDown::hookSignals();
    if (argc < 2) {
        printf("usage  : pingserver <listenspec>\n");
        printf("example: pingserver 'tcp/8000'\n");
        return 1;
    }

    FNET_Transport            transport;
    PingPacketFactory         factory;
    FNET_SimplePacketStreamer streamer(&factory);
    FNET_Connector*           listener = transport.Listen(argv[1], &streamer, this);
    if (listener != nullptr)
        listener->internal_subref();

    FNET_SignalShutDown ssd(transport);
    transport.Main();
    return 0;
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    PingServer myapp;
    return myapp.main(argc, argv);
}
