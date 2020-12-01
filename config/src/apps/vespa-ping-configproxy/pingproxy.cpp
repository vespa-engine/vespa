// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fastos/app.h>

#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("vespa-ping-configproxy");


class PingProxy : public FastOS_Application
{
private:
    std::unique_ptr<fnet::frt::StandaloneFRT> _server;
    FRT_Target     *_target;

public:
    PingProxy(const PingProxy &) = delete;
    PingProxy &operator=(const PingProxy &) = delete;
    PingProxy() : _server(), _target(nullptr) {}
    ~PingProxy() override ;
    int usage();
    void initRPC(const char *spec);
    void finiRPC();
    int Main() override;
};


PingProxy::~PingProxy()
{
    LOG_ASSERT(!_server);
    LOG_ASSERT(_target == nullptr);
}


int
PingProxy::usage()
{
    fprintf(stderr, "usage: %s\n", _argv[0]);
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
        _target->SubRef();
        _target = nullptr;
    }
    _server.reset();
}


int
PingProxy::Main()
{
    int retval = 0;
    bool debugging = false;
    char c = -1;

    const char *serverHost = "localhost";
    int clientTimeout = 5;
    int serverPort = 19090;

    const char *optArg = nullptr;
    int optInd = 0;
    while ((c = GetOpt("w:s:p:dh", optArg, optInd)) != -1) {
        switch (c) {
        case 'w':
            clientTimeout = atoi(optArg);
            break;
        case 's':
            serverHost = optArg;
            break;
        case 'p':
            serverPort = atoi(optArg);
            break;
        case 'd':
            debugging = true;
            break;
        case '?':
        default:
            retval = 1;
            [[fallthrough]];
        case 'h':
            usage();
            return retval;
        }
    }

    if (serverPort == 0) {
        usage();
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
    req->SubRef();
    finiRPC();
    return retval;
}

int main(int argc, char **argv)
{
    PingProxy app;
    return app.Entry(argc, argv);
}
