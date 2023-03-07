// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("vespa-slobrok-cmd");

class Slobrok_CMD
{
private:
    std::unique_ptr<fnet::frt::StandaloneFRT> _server;
    FRT_Target     *_target;

    Slobrok_CMD(const Slobrok_CMD &);
    Slobrok_CMD &operator=(const Slobrok_CMD &);

public:
    Slobrok_CMD() : _server(), _target(nullptr) {}
    ~Slobrok_CMD();
    int usage(const char *self);
    void initRPC(const char *spec);
    void finiRPC();
    int main(int argc, char **argv);
};

Slobrok_CMD::~Slobrok_CMD()
{
    LOG_ASSERT(! _server);
    LOG_ASSERT(_target == nullptr);
}

int
Slobrok_CMD::usage(const char *self)
{
    fprintf(stderr, "usage: %s <port|spec> <cmd> [args]\n", self);
    fprintf(stderr, "with cmd one of:\n");
    fprintf(stderr, "  slobrok.callback.listNamesServed\n");
    fprintf(stderr, "  slobrok.internal.listManagedRpcServers\n");
    fprintf(stderr, "  slobrok.admin.listAllRpcServers\n");
    fprintf(stderr, "  slobrok.lookupRpcServer {pattern}\n");
    fprintf(stderr, "  slobrok.registerRpcServer name {spec}\n");
    fprintf(stderr, "  slobrok.unregisterRpcServer {name} {spec}\n");
    fprintf(stderr, "  slobrok.admin.addPeer {name} {spec}\n");
    fprintf(stderr, "  slobrok.admin.removePeer {name} {spec}\n");
    fprintf(stderr, "  slobrok.system.stop\n");
    fprintf(stderr, "  slobrok.system.version\n");
    fprintf(stderr, "  system.stop\n");
    return 1;
}


void
Slobrok_CMD::initRPC(const char *spec)
{
    _server = std::make_unique<fnet::frt::StandaloneFRT>();
    _target     = _server->supervisor().GetTarget(spec);
}


void
Slobrok_CMD::finiRPC()
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
Slobrok_CMD::main(int argc, char **argv)
{
    if (argc < 3) {
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

    bool threeTables = false;
    bool twoTables = false;

    FRT_RPCRequest *req = _server->supervisor().AllocRPCRequest();

    req->SetMethodName(argv[2]);
    if (strcmp(argv[2], "slobrok.admin.listAllRpcServers") == 0) {
        threeTables = true;
        // no params
    } else if (strcmp(argv[2],  "slobrok.internal.listManagedRpcServers") == 0) {
        twoTables = true;
        // no params
    } else if (strcmp(argv[2], "slobrok.callback.listNamesServed") == 0
               || strcmp(argv[2], "slobrok.internal.listManagedRpcServers") == 0
               || strcmp(argv[2], "slobrok.admin.listAllRpcServers") == 0
               || strcmp(argv[2], "slobrok.system.stop") == 0
               || strcmp(argv[2], "slobrok.system.version") == 0
               || strcmp(argv[2], "system.stop") == 0)
    {
        // no params
    } else if (strcmp(argv[2], "slobrok.lookupRpcServer") == 0
               && argc == 4)
    {
        twoTables = true;
        // one param
        req->GetParams()->AddString(argv[3]);
    } else if ((strcmp(argv[2], "slobrok.registerRpcServer") == 0
                || strcmp(argv[2], "slobrok.unregisterRpcServer") == 0
                || strcmp(argv[2], "slobrok.admin.addPeer") == 0
                || strcmp(argv[2], "slobrok.admin.removePeer") == 0)
               && argc == 5)
    {
        // two params
        req->GetParams()->AddString(argv[3]);
        req->GetParams()->AddString(argv[4]);
    } else {
        finiRPC();
        return usage(argv[0]);
    }
    _target->InvokeSync(req, 5.0);

    if (req->IsError()) {
        fprintf(stderr, "vespa-slobrok-cmd error %d: %s\n",
                req->GetErrorCode(), req->GetErrorMessage());
    } else {
        FRT_Values &answer = *(req->GetReturn());
        const char *atypes = answer.GetTypeString();
        if (threeTables
            && strcmp(atypes, "SSS") == 0
            && answer[0]._string_array._len > 0
            && answer[0]._string_array._len == answer[1]._string_array._len
            && answer[0]._string_array._len == answer[2]._string_array._len)
        {
            for (uint32_t j = 0; j < answer[0]._string_array._len; j++) {
                printf("%s\t%s\t%s\n",
                       answer[0]._string_array._pt[j]._str,
                       answer[1]._string_array._pt[j]._str,
                       answer[2]._string_array._pt[j]._str);
            }
        } else if (twoTables
                   && strcmp(atypes, "SS") == 0
                   && answer[0]._string_array._len > 0
                   && answer[0]._string_array._len == answer[1]._string_array._len)
        {
            for (uint32_t j = 0; j < answer[0]._string_array._len; j++) {
                printf("%s\t%s\n",
                       answer[0]._string_array._pt[j]._str,
                       answer[1]._string_array._pt[j]._str);
            }
        } else {
            fprintf(stderr, "vespa-slobrok-cmd OK, returntypes '%s'\n", atypes);
            uint32_t idx = 0;
            while (atypes != nullptr && *atypes != '\0') {
                switch (*atypes) {
                case 's':
                    printf("    string = '%s'\n", answer[idx]._string._str);
                    break;
                case 'S':
                    printf("   strings [%d]\n", answer[idx]._string_array._len);
                    for (uint32_t j = 0; j < answer[idx]._string_array._len; j++) {
                        printf("\t'%s'\n",  answer[idx]._string_array._pt[j]._str);
                    }
                    break;
                default:
                    printf("   unknown type %c\n", *atypes);
                }
                ++atypes;
                ++idx;
            }
        }
    }
    req->internal_subref();
    finiRPC();
    return 0;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    Slobrok_CMD sb_cmd;
    return sb_cmd.main(argc, argv);
}
