// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fastos/app.h>
#include <vespa/vespalib/locale/c.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-rpc-invoke");

class RPCClient : public FastOS_Application
{
private:
    static bool addArg(FRT_RPCRequest *req, const char *param) {
        int len = strlen(param);
        if (len < 2 || param[1] != ':') {
            return false;
        }
        const char *value = param + 2;
        switch (param[0]) {
        case 'b':
            req->GetParams()->AddInt8(strtoll(value, nullptr, 0));
            break;
        case 'h':
            req->GetParams()->AddInt16(strtoll(value, nullptr, 0));
            break;
        case 'i':
            req->GetParams()->AddInt32(strtoll(value, nullptr, 0));
            break;
        case 'l':
            req->GetParams()->AddInt64(strtoll(value, nullptr, 0));
            break;
        case 'f':
            req->GetParams()->AddFloat(vespalib::locale::c::strtod(value, nullptr));
            break;
        case 'd':
            req->GetParams()->AddDouble(vespalib::locale::c::strtod(value, nullptr));
            break;
        case 's':
            req->GetParams()->AddString(value);
            break;
        default:
            return false;
        }
        return true;
    }
    int run();

public:
    int Main() override;
};

int
RPCClient::Main()
{
    if (_argc < 3) {
        fprintf(stderr, "usage: vespa-rpc-invoke [-t timeout] <connectspec> <method> [args]\n");
        fprintf(stderr, "    -t timeout in seconds\n");
        fprintf(stderr, "    Each arg must be on the form <type>:<value>\n");
        fprintf(stderr, "    supported types: {'b','h','i','l','f','d','s'}\n");
        return 1;
    }
    try {
        return run();
    } catch (const std::exception & e) {
        fprintf(stderr, "Caught exception : '%s'", e.what());
        return 2;
    }
}

int
RPCClient::run()
{
    int retCode = 0;
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor & supervisor = server.supervisor();
    int targetArg = 1;
    int methNameArg = 2;
    int startOfArgs = 3;
    int timeOut = 10;
    if (strcmp(_argv[1], "-t") == 0) {
      timeOut = atoi(_argv[2]);
      targetArg = 3;
      methNameArg = 4;
      startOfArgs = 5;
    }
    FRT_Target *target = supervisor.GetTarget(_argv[targetArg]);
    FRT_RPCRequest *req = supervisor.AllocRPCRequest();
    req->SetMethodName(_argv[methNameArg]);
    for (int i = startOfArgs; i < _argc; ++i) {
        if (!addArg(req, _argv[i])) {
            fprintf(stderr, "could not parse parameter: '%s'\n", _argv[i]);
            retCode = 2;
            break;
        }
    }
    if (retCode == 0) {
        fprintf(stdout, "PARAMETERS:\n");
        req->GetParams()->Print();
        target->InvokeSync(req, (double)timeOut);
        if (req->GetErrorCode() == FRTE_NO_ERROR) {
            fprintf(stdout, "RETURN VALUES:\n");
            req->GetReturn()->Print();
        } else {
            fprintf(stderr, "error(%d): %s\n",
                    req->GetErrorCode(),
                    req->GetErrorMessage());
            retCode = 3;
        }
    }
    req->SubRef();
    target->SubRef();
    return retCode;
}


int
main(int argc, char **argv)
{
    RPCClient myapp;
    return myapp.Entry(argc, argv);
}
