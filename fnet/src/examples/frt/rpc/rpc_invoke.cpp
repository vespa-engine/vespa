// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/signalhandler.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-rpc-invoke");

class RPCClient {
private:
    static bool addArg(FRT_RPCRequest* req, const char* param) {
        int len = strlen(param);
        if (len < 2 || param[1] != ':') {
            return false;
        }
        const char* value = param + 2;
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
    int run(int argc, char** argv);

public:
    int main(int argc, char** argv);
};

bool timeout_specified(char** argv) { return strcmp(argv[1], "-t") == 0; }

int RPCClient::main(int argc, char** argv) {
    if ((argc < 3) || (timeout_specified(argv) && argc < 5)) {
        fprintf(stderr, "usage: vespa-rpc-invoke [-t timeout] <connectspec> <method> [args]\n");
        fprintf(stderr, "    -t timeout in seconds\n");
        fprintf(stderr, "    Each arg must be on the form <type>:<value>\n");
        fprintf(stderr, "    supported types: {'b','h','i','l','f','d','s'}\n");
        return 1;
    }
    try {
        return run(argc, argv);
    } catch (const std::exception& e) {
        fprintf(stderr, "Caught exception : '%s'", e.what());
        return 2;
    }
}

int RPCClient::run(int argc, char** argv) {
    int                      retCode = 0;
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor&          supervisor = server.supervisor();
    int                      targetArg = 1;
    int                      methNameArg = 2;
    int                      startOfArgs = 3;
    int                      timeOut = 10;
    if (timeout_specified(argv)) {
        timeOut = atoi(argv[2]);
        targetArg = 3;
        methNameArg = 4;
        startOfArgs = 5;
    }
    FRT_Target*     target = supervisor.GetTarget(argv[targetArg]);
    FRT_RPCRequest* req = supervisor.AllocRPCRequest();
    req->SetMethodName(argv[methNameArg]);
    for (int i = startOfArgs; i < argc; ++i) {
        if (!addArg(req, argv[i])) {
            fprintf(stderr, "could not parse parameter: '%s'\n", argv[i]);
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
            fprintf(stderr, "error(%d): %s\n", req->GetErrorCode(), req->GetErrorMessage());
            retCode = 3;
        }
    }
    req->internal_subref();
    target->internal_subref();
    return retCode;
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    RPCClient myapp;
    return myapp.main(argc, argv);
}
