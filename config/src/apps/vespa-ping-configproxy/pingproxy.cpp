// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <unistd.h>

#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("vespa-ping-configproxy");


class PingProxy
{
private:
    std::unique_ptr<fnet::frt::StandaloneFRT> _server;
    FRT_Target     *_target;

public:
    PingProxy(const PingProxy &) = delete;
    PingProxy &operator=(const PingProxy &) = delete;
    PingProxy() : _server(), _target(nullptr) {}
    ~PingProxy();
    int usage(const char *self);
    void initRPC(const char *spec);
    void finiRPC();
    int main(int argc, char **argv);
};


PingProxy::~PingProxy()
{
    LOG_ASSERT(!_server);
    LOG_ASSERT(_target == nullptr);
}


int
PingProxy::usage(const char *self)
{
    fprintf(stderr, "usage: %s\n", self);
    fprintf(stderr, "-s [server]        (server hostname, default localhost)\n");
    fprintf(stderr, "-p [port]          (server port number, default 19090)\n");
    return 1;
}


void
PingProxy::initRPC(const char *spec)
{
    _server = std::make_unique<fnet::frt::StandaloneFRT>();
    _target     = _server->supervisor().GetTarget(spec);
}


void
PingProxy::finiRPC()
{
    if (_target != nullptr) {
        _target->internal_subref();
        _target = nullptr;
    }
    _server.reset();
}


int
PingProxy::main(int argc, char **argv)
{
    int retval = 0;
    bool debugging = false;
    int c = -1;

    const char *serverHost = "localhost";
    int clientTimeout = 5;
    int serverPort = 19090;

    while ((c = getopt(argc, argv, "w:s:p:dh")) != -1) {
        switch (c) {
        case 'w':
            clientTimeout = atoi(optarg);
            break;
        case 's':
            serverHost = optarg;
            break;
        case 'p':
            serverPort = atoi(optarg);
            break;
        case 'd':
            debugging = true;
            break;
        case '?':
        default:
            retval = 1;
            [[fallthrough]];
        case 'h':
            usage(argv[0]);
            return retval;
        }
    }

    if (serverPort == 0) {
        usage(argv[0]);
        return 1;
    }

    std::ostringstream tmp;
    tmp << "tcp/";
    tmp << serverHost;
    tmp << ":";
    tmp << serverPort;
    std::string sspec = tmp.str();
    const char *spec = sspec.c_str();
    if (debugging) {
        printf("connecting to '%s'\n", spec);
        LOG(info, "connecting to '%s'\n", spec);
    }
    try {
        initRPC(spec);
    } catch (std::exception& ex) {
        LOG(error, "Got exception while initializing RPC: '%s'", ex.what());
        return 1;
    }

    FRT_RPCRequest *req = _server->supervisor().AllocRPCRequest();

    req->SetMethodName("ping");

    _target->InvokeSync(req, clientTimeout); // seconds

    if (req->IsError()) {
        retval = 1;
        fprintf(stderr, "error %d: %s\n",
                req->GetErrorCode(), req->GetErrorMessage());
    } else {
        FRT_Values &answer = *(req->GetReturn());
        const char *atypes = answer.GetTypeString();
        if (strcmp(atypes, "i") == 0) {
            if (debugging) {
                printf("ping %d\n", answer[0]._intval32);
            }
        } else {
            fprintf(stderr, "unexpected return types in RPC answer: '%s'\n", atypes);
            retval = 1;
        }
    }
    req->internal_subref();
    finiRPC();
    return retval;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    PingProxy app;
    return app.main(argc, argv);
}
