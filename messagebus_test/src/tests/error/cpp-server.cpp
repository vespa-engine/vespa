// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("cpp-server");
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/iprotocol.h>
#include <vespa/messagebus/protocolset.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>
#include <vespa/messagebus/errorcode.h>

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

class App : public FastOS_Application
{
public:
    int Main() override;
};

int
App::Main()
{
    RPCMessageBus mb(ProtocolSet().add(IProtocol::SP(new SimpleProtocol())),
                     RPCNetworkParams()
                     .setIdentity(Identity("server/cpp"))
                     .setSlobrokConfig("file:slobrok.cfg"),
                     "file:routing.cfg");
    Server server(mb.getMessageBus());
    while (true) {
        FastOS_Thread::Sleep(1000);
    }
    return 0;
}

int main(int argc, char **argv) {
    App app;
    return app.Entry(argc, argv);
}
