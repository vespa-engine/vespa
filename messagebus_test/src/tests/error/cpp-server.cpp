// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/vespalib/util/time.h>
#include <thread>
#include <vespa/vespalib/util/signalhandler.h>

using namespace mbus;

class Server : public IMessageHandler
{
private:
    DestinationSession::UP _session;
public:
    Server(MessageBus &bus);
    ~Server();
    void handleMessage(Message::UP msg) override;
};

Server::Server(MessageBus &bus)
    : _session(bus.createDestinationSession("session", true, *this))
{
    fprintf(stderr, "cpp server started\n");
}

Server::~Server()
{
    _session.reset();
}

void
Server::handleMessage(Message::UP msg) {
    Reply::UP reply(new EmptyReply());
    msg->swapState(*reply);
    reply->addError(Error(ErrorCode::APP_FATAL_ERROR + 1, "ERR 1"));
    reply->addError(Error(ErrorCode::APP_FATAL_ERROR + 2, "ERR 2"));
    _session->reply(std::move(reply));
}

int main(int, char **) {
    vespalib::SignalHandler::PIPE.ignore();
    RPCMessageBus mb(ProtocolSet().add(std::make_shared<SimpleProtocol>()),
                     RPCNetworkParams(config::ConfigUri("file:slobrok.cfg"))
                     .setIdentity(Identity("server/cpp")),
                     config::ConfigUri("file:routing.cfg"));
    Server server(mb.getMessageBus());
    while (true) {
        std::this_thread::sleep_for(1s);
    }
    return 0;
}
