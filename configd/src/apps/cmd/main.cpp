// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>

#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-sentinel-cmd");

namespace {
struct Method {
    const char * name;
    const char * rpcMethod;
    bool noArgNeeded;
    bool needsTimeoutArg;
};
const Method methods[] = {
    { "list", "sentinel.ls", true, false },
    { "restart", "sentinel.service.restart", false, false },
    { "start", "sentinel.service.start", false, false },
    { "stop", "sentinel.service.stop", false, false },
    { "connectivity", "sentinel.report.connectivity", true, true }
};

}


class Cmd
{
private:
    std::unique_ptr<fnet::frt::StandaloneFRT> _server;
    FRT_Target *_target;

public:
    Cmd() : _server(), _target(nullptr) {}
    ~Cmd();
    int run(const Method &cmd, const char *arg);
    void initRPC(const char *spec);
    void finiRPC();
};

Cmd::~Cmd()
{
    LOG_ASSERT(! _server);
    LOG_ASSERT(_target == nullptr);
}

void usage()
{
    fprintf(stderr, "usage: vespa-sentinel-cmd <cmd> [arg]\n");
    fprintf(stderr, "with cmd one of:\n");
    fprintf(stderr, "  list\n");
    fprintf(stderr, "  restart {service}\n");
    fprintf(stderr, "  start {service}\n");
    fprintf(stderr, "  stop {service}\n");
    fprintf(stderr, "  connectivity [milliseconds]\n");
}

void
Cmd::initRPC(const char *spec)
{
    _server = std::make_unique<fnet::frt::StandaloneFRT>();
    _target     = _server->supervisor().GetTarget(spec);
}


void
Cmd::finiRPC()
{
    if (_target != nullptr) {
        _target->SubRef();
        _target = nullptr;
    }
    _server.reset();
}


int
Cmd::run(const Method &cmd, const char *arg)
{
    int retval = 0;
    try {
        initRPC("tcp/localhost:19097");
    } catch (vespalib::Exception &e) {
        fprintf(stderr, "vespa-sentinel-cmd: exception in network initialization: %s\n",
                e.what());
        return 2;
    }
    FRT_RPCRequest *req = _server->supervisor().AllocRPCRequest();
    req->SetMethodName(cmd.rpcMethod);

    int pingTimeoutMs = 5000;
    if (cmd.needsTimeoutArg) {
        if (arg) {
            pingTimeoutMs = atoi(arg);
        }
        req->GetParams()->AddInt32(pingTimeoutMs);
    } else if (arg) {
        // one param
        req->GetParams()->AddString(arg);
    }
    _target->InvokeSync(req, 2 * pingTimeoutMs * 0.001);

    if (req->IsError()) {
        fprintf(stderr, "vespa-sentinel-cmd '%s' error %d: %s\n",
                cmd.name, req->GetErrorCode(), req->GetErrorMessage());
        retval = 1;
    } else {
        FRT_Values &answer = *(req->GetReturn());
        const char *atypes = answer.GetTypeString();
        fprintf(stderr, "vespa-sentinel-cmd '%s' OK.\n", cmd.name);
        if (atypes && (strcmp(atypes, "SS") == 0)) {
            uint32_t numHosts = answer[0]._string_array._len;
            uint32_t numStats = answer[1]._string_array._len;
            FRT_StringValue *hosts = answer[0]._string_array._pt;
            FRT_StringValue *stats = answer[1]._string_array._pt;
            uint32_t ml = 0;
            uint32_t j;
            for (j = 0; j < numHosts; ++j) {
                uint32_t hl = strlen(hosts[j]._str);
                if (hl > ml) ml = hl;
            }
            for (j = 0; j < numHosts && j < numStats; ++j) {
                printf("%-*s -> %s\n", ml, hosts[j]._str, stats[j]._str);
            }
            for (; j < numHosts; ++j) {
                printf("Extra host: %s\n", hosts[j]._str);
            }
            for (; j < numStats; ++j) {
                printf("Extra stat: %s\n", stats[j]._str);
            }
        } else {
            uint32_t idx = 0;
            while (atypes != nullptr && *atypes != '\0') {
                switch (*atypes) {
                case 's':
                    printf("%s\n", answer[idx]._string._str);
                    break;
                default:
                    printf("BAD: unknown type %c\n", *atypes);
                }
                ++atypes;
            ++idx;
            }
        }
    }
    req->SubRef();
    finiRPC();
    return retval;
}

const Method *
parseCmd(const char *arg)
{
    for (const auto & method : methods) {
        if (strcmp(arg, method.name) == 0) {
            return &method;
        }
    }
    return nullptr;
}

void hookSignals() {
    using SIG = vespalib::SignalHandler;
    SIG::PIPE.ignore();
}

int main(int argc, char** argv)
{
    int retval = 1;
    const Method *cmd = nullptr;
    if (argc > 1) {
        cmd = parseCmd(argv[1]);
    }
    const char *extraArg = (argc > 2 ? argv[2] : nullptr);
    if (cmd && (extraArg || cmd->noArgNeeded)) {
        hookSignals();
        Cmd runner;
        retval = runner.run(*cmd, extraArg);
    } else {
        usage();
    }
    return retval;
}
