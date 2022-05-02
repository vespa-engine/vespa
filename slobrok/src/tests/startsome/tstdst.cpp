// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/frt/target.h>
#include <sstream>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("testrpcserver");

class FNET_Transport;
class FNET_Scheduler;
class FRT_Supervisor;

namespace testrpcserver {

class RPCHooks;

class TstEnv : public FRT_IRequestWait
{
private:
    FNET_Transport    *_transport;
    FRT_Supervisor    *_supervisor;

    int                _myport;
    int                _sbport;
    RPCHooks          *_rpcHooks;

    FNET_Transport *getTransport() { return _transport; }
    FRT_Supervisor *getSupervisor() { return _supervisor; }

    TstEnv(const TstEnv &);            // Not used
    TstEnv &operator=(const TstEnv &); // Not used
public:
    const char * const _id;

    explicit TstEnv(int sbp, int myp, const char *n);
    virtual ~TstEnv();

    int MainLoop();

    void shutdown() { getTransport()->ShutDown(false); }
    void RequestDone(FRT_RPCRequest* req) override {
        if (req->IsError()) {
            LOG(error, "registration failed: %s", req->GetErrorMessage());
        } else {
            LOG(debug, "registered");
        }
    }
};


class RPCHooks : public FRT_Invokable
{
private:
    TstEnv &_env;

    RPCHooks(const RPCHooks &);            // Not used
    RPCHooks &operator=(const RPCHooks &); // Not used
public:
    explicit RPCHooks(TstEnv &env);
    virtual ~RPCHooks();

    void initRPC(FRT_Supervisor *supervisor);
private:
    void rpc_listNamesServed(FRT_RPCRequest *req);

    void rpc_stop(FRT_RPCRequest *req);
};


RPCHooks::RPCHooks(TstEnv &env)
    : _env(env)
{
}
RPCHooks::~RPCHooks()
{
}


void
RPCHooks::initRPC(FRT_Supervisor *supervisor)
{

    FRT_ReflectionBuilder rb(supervisor);
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.callback.listNamesServed", "", "S",
                    FRT_METHOD(RPCHooks::rpc_listNamesServed), this);
    rb.MethodDesc("Look up a rpcserver");
    rb.ReturnDesc("names", "The rpcserver names on this server");
    //-------------------------------------------------------------------------
    rb.DefineMethod("system.stop", "", "",
                    FRT_METHOD(RPCHooks::rpc_stop), this);
    rb.MethodDesc("Shut down the application");
    //-------------------------------------------------------------------------
}


void
RPCHooks::rpc_listNamesServed(FRT_RPCRequest *req)
{
    std::vector<const char *> rpcsrvlist;
    rpcsrvlist.push_back("testrpcsrv/17");
    rpcsrvlist.push_back("testrpcsrv/191");
    rpcsrvlist.push_back(_env._id);

    FRT_Values &dst = *req->GetReturn();

    FRT_StringValue *names = dst.AddStringArray(rpcsrvlist.size());

    for (uint32_t i = 0; i < rpcsrvlist.size(); ++i) {
        dst.SetString(&names[i], rpcsrvlist[i]);
    }
    if (rpcsrvlist.size() < 1) {
        req->SetError(FRTE_RPC_METHOD_FAILED, "no rpcserver names");
    }
}


// System API methods
void
RPCHooks::rpc_stop(FRT_RPCRequest *req)
{
    (void) req;
    LOG(debug, "RPC: Shutdown");
    _env.shutdown();
}


TstEnv::TstEnv(int sbp, int myp, const char *n)
    : _transport(new FNET_Transport()),
      _supervisor(new FRT_Supervisor(_transport)),
      _myport(myp),
      _sbport(sbp),
      _rpcHooks(NULL),
      _id(n)
{
    _rpcHooks = new RPCHooks(*this);
    _rpcHooks->initRPC(getSupervisor());
}


TstEnv::~TstEnv()
{
    delete _rpcHooks;
}


int
TstEnv::MainLoop()
{
    srandom(time(NULL));

    if (! getSupervisor()->Listen(_myport)) {
        LOG(error, "TestRpcServer: unable to listen to port %d", _myport);
        return 1;
    }

    std::string myrpcsrv = _id;

    std::ostringstream tmp;
    tmp << "tcp/" << vespalib::HostName::get() << ":" << _myport;
    std::string myspec = tmp.str();
    tmp.str("");
    tmp << "tcp/" << vespalib::HostName::get() << ":" << _sbport;
    std::string sbspec = tmp.str();

    FRT_RPCRequest *req = getSupervisor()->AllocRPCRequest();
    req->SetMethodName("slobrok.registerRpcServer");
    req->GetParams()->AddString(myrpcsrv.c_str());
    req->GetParams()->AddString(myspec.c_str());
    FRT_Target *slobrok = getSupervisor()->GetTarget(sbspec.c_str());
    slobrok->InvokeAsync(req, 5.0, this);
    getTransport()->Main();
    getTransport()->WaitFinished();
    return 0;
}


class App
{
public:
    int main(int argc, char **argv) {
        int sbport = 2773;
        int myport = 2774;
        const char *rpcsrvname = "testrpcsrv/17";

        int c;
        while ((c = getopt(argc, argv, "n:p:s:")) != -1) {
            switch (c) {
            case 'p':
                myport = atoi(optarg);
                break;
            case 's':
                sbport = atoi(optarg);
                break;
            case 'n':
                rpcsrvname = optarg;
                break;
            default:
                LOG(error, "unknown option letter '%c'", c);
                return 1;
            }
        }

        TstEnv *mainobj = new TstEnv(sbport, myport, rpcsrvname);
        int res = mainobj->MainLoop();
        delete mainobj;
        return res;
    }
};

} // namespace testrpcserver

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    testrpcserver::App tstdst;
    return tstdst.main(argc, argv);
}
