// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <thread>
#include <vespa/vespalib/util/signalhandler.h>

using namespace mbus;
using namespace std::chrono_literals;

class App
{
public:
    int main(int argc, char **argv);
};

int
App::main(int, char **)
{
    RPCMessageBus mb(ProtocolSet().add(std::make_shared<SimpleProtocol>()),
                     RPCNetworkParams(config::ConfigUri("file:slobrok.cfg"))
                     .setIdentity(Identity("server/cpp")),
                     config::ConfigUri("file:routing.cfg"));

    Receptor src;
    Message::UP msg;
    Reply::UP reply;

    SourceSession::UP ss = mb.getMessageBus().createSourceSession(src, SourceSessionParams().setTimeout(300s));
    for (int i = 0; i < 10; ++i) {
        msg = std::make_unique<SimpleMessage>("test");
        msg->getTrace().setLevel(9);
        ss->send(std::move(msg), "test");
        reply = src.getReply(600s); // 10 minutes timeout
        if ( ! reply) {
            fprintf(stderr, "CPP-CLIENT: no reply\n");
        } else {
            fprintf(stderr, "CPP-CLIENT:\n%s\n",
                    reply->getTrace().toString().c_str());
            if (reply->getNumErrors() == 2) {
                break;
            }
        }
        std::this_thread::sleep_for(1s);
    }
    if ( ! reply) {
        fprintf(stderr, "CPP-CLIENT: no reply\n");
        return 1;
    }
    if (reply->getNumErrors() != 2 ||
        reply->getError(0).getCode() != (ErrorCode::APP_FATAL_ERROR + 1) ||
        reply->getError(1).getCode() != (ErrorCode::APP_FATAL_ERROR + 2) ||
        reply->getError(0).getMessage() != "ERR 1" ||
        reply->getError(1).getMessage() != "ERR 2")
    {
        fprintf(stderr, "CPP-CLIENT: wrong errors\n");
        return 1;
    }
    return 0;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
