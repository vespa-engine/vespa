// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fnet/frt/frt.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/fastos/app.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-storage-cmd");

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
            req->GetParams()->AddInt8(strtoll(value, NULL, 0));
            break;
        case 'h':
            req->GetParams()->AddInt16(strtoll(value, NULL, 0));
            break;
        case 'i':
            req->GetParams()->AddInt32(strtoll(value, NULL, 0));
            break;
        case 'l':
            req->GetParams()->AddInt64(strtoll(value, NULL, 0));
            break;
        case 'f':
            req->GetParams()->AddFloat(strtod(value, NULL));
            break;
        case 'd':
            req->GetParams()->AddDouble(strtod(value, NULL));
            break;
        case 's':
            req->GetParams()->AddString(value);
            break;
        default:
            return false;
        }
        return true;
    }

public:
    int Main() override {
        if (_argc < 3) {
            fprintf(stderr, "usage: vespa-storage-cmd <connectspec> <method> [args]\n");
            fprintf(stderr, "Calls RPC method on a storage/distributor process\n");
            fprintf(stderr, "Call frt.rpc.getMethodList to get available RPC methods\n");
            fprintf(stderr, "    each arg must be on the form <type>:<value>\n");
            fprintf(stderr, "    supported types: {'b','h','i','l','f','d','s'}\n");
            return 1;
        }
        int retCode = 0;
        FRT_Supervisor supervisor;
        supervisor.Start();

        slobrok::ConfiguratorFactory sbcfg("admin/slobrok.0");
        slobrok::api::MirrorAPI mirror(supervisor, sbcfg);

        while (!mirror.ready()) {
            FastOS_Thread::Sleep(10);
        }

        slobrok::api::MirrorAPI::SpecList list = mirror.lookup(_argv[1]);

        if (list.size() == 0) {
            fprintf(stderr, "No servers found matching %s\n", _argv[1]);
        }

        for (size_t j = 0; j < list.size(); j++) {
            FRT_Target *target = supervisor.GetTarget(list[j].second.c_str());

            // If not fleet controller, need to connect first.
            if (strstr(_argv[1], "fleetcontroller") == NULL) {
                FRT_RPCRequest *req = supervisor.AllocRPCRequest();
                req->SetMethodName("vespa.storage.connect");
                req->GetParams()->AddString(_argv[1]);
                target->InvokeSync(req, 10.0);

                if (req->GetErrorCode() != FRTE_NO_ERROR) {
                    fprintf(stderr, "error(%d): %s\n",
                        req->GetErrorCode(),
                        req->GetErrorMessage());
                    continue;
                }
                req->SubRef();
            }

            FRT_RPCRequest *req = supervisor.AllocRPCRequest();
            req->SetMethodName(_argv[2]);

            for (int i = 3; i < _argc; ++i) {
                if (!addArg(req, _argv[i])) {
                    fprintf(stderr, "could not parse parameter: '%s'\n", _argv[i]);
                    retCode = 2;
                    break;
                }
            }
            if (retCode == 0) {
                target->InvokeSync(req, 10.0);
                if (req->GetErrorCode() == FRTE_NO_ERROR) {
                    fprintf(stdout, "RETURN VALUES FOR %s:\n", list[j].first.c_str());
                    req->GetReturn()->Print();
                    retCode = 3;
                } else {
                    fprintf(stderr, "error(%d): %s\n",
                        req->GetErrorCode(),
                        req->GetErrorMessage());
                }
            }
            req->SubRef();
            target->SubRef();
        }
        supervisor.ShutDown(true);
        return retCode;
    }
};

int
main(int argc, char **argv)
{
  RPCClient myapp;
  return myapp.Entry(argc, argv);
}
