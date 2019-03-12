
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>

#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-sentinel-cmd");

class Cmd
{
private:
    std::unique_ptr<FRT_Supervisor> _supervisor;
    FRT_Target *_target;

public:
    Cmd() : _supervisor(), _target(nullptr) {}
    ~Cmd();
    int run(const char *cmd, const char *arg);
    void initRPC(const char *spec);
    void finiRPC();
};

Cmd::~Cmd()
{
    LOG_ASSERT(! _supervisor);
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
}

void
Cmd::initRPC(const char *spec)
{
    _supervisor = std::make_unique<FRT_Supervisor>();
    _target     = _supervisor->GetTarget(spec);
    _supervisor->Start();
}


void
Cmd::finiRPC()
{
    if (_target != nullptr) {
        _target->SubRef();
        _target = nullptr;
    }
    if (_supervisor) {
        _supervisor->ShutDown(true);
        _supervisor.reset();
    }
}


int
Cmd::run(const char *cmd, const char *arg)
{
    int retval = 0;
    initRPC("tcp/localhost:19097");

    FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
    req->SetMethodName(cmd);

    if (arg) {
        // one param
        req->GetParams()->AddString(arg);
    }
    _target->InvokeSync(req, 5.0);

    if (req->IsError()) {
        fprintf(stderr, "vespa-sentinel-cmd '%s' error %d: %s\n",
                cmd, req->GetErrorCode(), req->GetErrorMessage());
        retval = 1;
    } else {
        FRT_Values &answer = *(req->GetReturn());
        const char *atypes = answer.GetTypeString();
        fprintf(stderr, "vespa-sentinel-cmd '%s' OK.\n", cmd);
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
    req->SubRef();
    finiRPC();
    return retval;
}

const char *
parseCmd(const char *arg)
{
    if (strcmp(arg, "list") == 0) {
        return "sentinel.ls";
    } else if (strcmp(arg, "restart") == 0) {
        return "sentinel.service.restart";
    } else if (strcmp(arg, "start") == 0) {
        return "sentinel.service.start";
    } else if (strcmp(arg, "stop") == 0) {
        return "sentinel.service.stop";
    }
    return 0;
}

void hookSignals() {
    using SIG = vespalib::SignalHandler;
    SIG::PIPE.ignore();
}

int main(int argc, char** argv)
{
    int retval = 1;
    const char *cmd = 0;
    if (argc > 1) {
        cmd = parseCmd(argv[1]);
    }
    if (cmd) {
        hookSignals();
        Cmd runner;
        retval = runner.run(cmd, argc > 2 ? argv[2] : 0);
    } else {
        usage();
    }
    return retval;
}
