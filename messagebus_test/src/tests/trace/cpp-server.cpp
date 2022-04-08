// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/vespalib/util/time.h>
#include <thread>
#include <vespa/vespalib/util/signalhandler.h>

using namespace mbus;

class Server : public IMessageHandler,
               public IReplyHandler
{
private:
    IntermediateSession::UP _session;
    std::string             _name;
public:
    Server(MessageBus &bus, const std::string &name);
    ~Server();
    void handleMessage(Message::UP msg) override;
    void handleReply(Reply::UP reply) override;
};

Server::Server(MessageBus &bus, const std::string &name)
    : _session(bus.createIntermediateSession("session", true, *this, *this)),
      _name(name)
{
    fprintf(stderr, "cpp server started: %s\n", _name.c_str());
}

Server::~Server()
{
    _session.reset();
}

void
Server::handleMessage(Message::UP msg) {
    msg->getTrace().trace(1, _name + " (message)", false);
    if (!msg->getRoute().hasHops()) {
        fprintf(stderr, "**** Server '%s' replying.\n", _name.c_str());
        auto reply = std::make_unique<EmptyReply>();
        msg->swapState(*reply);
        handleReply(std::move(reply));
    } else {
        fprintf(stderr, "**** Server '%s' forwarding message.\n", _name.c_str());
        _session->forward(std::move(msg));
    }
}

void
Server::handleReply(Reply::UP reply) {
    reply->getTrace().trace(1, _name + " (reply)", false);
    _session->forward(std::move(reply));
}

class App
{
public:
    int main(int argc, char **argv);
};

int
App::main(int argc, char **argv)
{
    if (argc != 2) {
        fprintf(stderr, "usage: %s <service-prefix>\n", argv[0]);
        return 1;
    }
    RPCMessageBus mb(ProtocolSet().add(std::make_shared<SimpleProtocol>()),
                     RPCNetworkParams(config::ConfigUri("file:slobrok.cfg"))
                     .setIdentity(Identity(argv[1])),
                     config::ConfigUri("file:routing.cfg"));
    Server server(mb.getMessageBus(), argv[1]);
    while (true) {
        std::this_thread::sleep_for(1s);
    }
    return 0;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
