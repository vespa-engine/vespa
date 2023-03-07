// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("check_slobrok");

class Slobrok_Checker
{
private:
    std::unique_ptr<fnet::frt::StandaloneFRT>  _server;
    FRT_Target                      *_target;

    Slobrok_Checker(const Slobrok_Checker &);
    Slobrok_Checker &operator=(const Slobrok_Checker &);

public:
    Slobrok_Checker() : _server(), _target(nullptr) {}
    ~Slobrok_Checker();
    int usage(const char *self);
    void initRPC(const char *spec);
    void finiRPC();
    int main(int argc, char **argv);
};

Slobrok_Checker::~Slobrok_Checker()
{
    LOG_ASSERT( !_server);
    LOG_ASSERT(_target == nullptr);
}


int
Slobrok_Checker::usage(const char *self)
{
    fprintf(stderr, "usage: %s <port>\n", self);
    return 1;
}


void
Slobrok_Checker::initRPC(const char *spec)
{
    _server = std::make_unique<fnet::frt::StandaloneFRT>();
    _target     = _server->supervisor().GetTarget(spec);
}


void
Slobrok_Checker::finiRPC()
{
    if (_target != nullptr) {
        _target->internal_subref();
        _target = nullptr;
    }
    if (_server) {
        _server.reset();
    }
}


int
Slobrok_Checker::main(int argc, char **argv)
{
    if (argc != 2) {
        return usage(argv[0]);
    }
    int port = atoi(argv[1]);
    if (port == 0) {
        initRPC(argv[1]);
    } else {
        std::ostringstream tmp;
        tmp << "tcp/localhost:";
        tmp << port;
        initRPC(tmp.str().c_str());
    }

    FRT_RPCRequest *req = _server->supervisor().AllocRPCRequest();

    req->SetMethodName("slobrok.system.version");
    _target->InvokeSync(req, 5.0);
    int failed = 0;

    if (req->IsError()) {
        printf("vespa_slobrok %d: %s\n",
               req->GetErrorCode(), req->GetErrorMessage());
        failed = 1;
    } else {
        FRT_Values &answer = *(req->GetReturn());
        const char *atypes = answer.GetTypeString();
        if (strcmp(atypes, "s") == 0) {
            printf("vespa_slobrok-%s OK\n", answer[0]._string._str);
        } else {
            printf("vespa_slobrok bad rpc return type %s\n", atypes);
            failed = 1;
        }
    }
    finiRPC();
    return failed;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    Slobrok_Checker sb_checker;
    return sb_checker.main(argc, argv);
}
