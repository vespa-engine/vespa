// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "translogclient.h"
#include "common.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/size_literals.h>


#include <vespa/log/log.h>
LOG_SETUP(".translogclient");

using namespace std::chrono_literals;

VESPA_THREAD_STACK_TAG(translogclient_rpc_callback)

namespace search::transactionlog::client {

namespace {
    const double NEVER(-1.0);
}

namespace {

struct RpcTask : public vespalib::Executor::Task {
    FRT_RPCRequest *req;
    std::function<void(FRT_RPCRequest *req)> fun;
    RpcTask(FRT_RPCRequest *req_in, std::function<void(FRT_RPCRequest *req)> &&fun_in)
        : req(req_in), fun(std::move(fun_in)) {}
    void run() override {
        fun(req);
        req->Return();
        req = nullptr;
    }
    ~RpcTask() override {
        if (req != nullptr) {
            req->SetError(FRTE_RPC_METHOD_FAILED, "client has been shut down");
            req->Return();
        }
    }
};

}

TransLogClient::TransLogClient(FNET_Transport & transport, const vespalib::string & rpcTarget) :
    _executor(std::make_unique<vespalib::ThreadStackExecutor>(1, translogclient_rpc_callback)),
    _rpcTarget(rpcTarget),
    _sessions(),
    _supervisor(std::make_unique<FRT_Supervisor>(&transport)),
    _target(nullptr)
{
    reconnect();
    exportRPC(*_supervisor);
}

TransLogClient::~TransLogClient()
{
    disconnect();
    _executor->shutdown().sync();
    _supervisor->GetTransport()->sync();
}

bool
TransLogClient::reconnect()
{
    disconnect();
    _target = _supervisor->Get2WayTarget(_rpcTarget.c_str());
    return isConnected();
}

bool
TransLogClient::isConnected() const {
    return (_target != nullptr) && _target->IsValid();
}

void
TransLogClient::disconnect()
{
    if (_target) {
        _target->internal_subref();
    }
}

bool
TransLogClient::create(const vespalib::string & domain)
{
    FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
    req->SetMethodName("createDomain");
    req->GetParams()->AddString(domain.c_str());
    int32_t retval(rpc(req));
    req->internal_subref();
    return (retval == 0);
}

bool
TransLogClient::remove(const vespalib::string & domain)
{
    FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
    req->SetMethodName("deleteDomain");
    req->GetParams()->AddString(domain.c_str());
    int32_t retval(rpc(req));
    req->internal_subref();
    return (retval == 0);
}

std::unique_ptr<Session>
TransLogClient::open(const vespalib::string & domain)
{
    FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
    req->SetMethodName("openDomain");
    req->GetParams()->AddString(domain.c_str());
    int32_t retval(rpc(req));
    req->internal_subref();
    if (retval == 0) {
        return std::make_unique<Session>(domain, *this);
    }
    return std::unique_ptr<Session>();
}

std::unique_ptr<Visitor>
TransLogClient::createVisitor(const vespalib::string & domain, Callback & callBack)
{
    return std::make_unique<Visitor>(domain, *this, callBack);
}

bool
TransLogClient::listDomains(std::vector<vespalib::string> & dir)
{
    FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
    req->SetMethodName("listDomains");
    int32_t retval(rpc(req));
    if (retval == 0) {
        char * s = req->GetReturn()->GetValue(1)._string._str;
        for (const char * d(strsep(&s, "\n")); d && (*d != '\0'); d = strsep(&s, "\n")) {
            dir.push_back(d);
        }
    }
    req->internal_subref();
    return (retval == 0);
}

int32_t
TransLogClient::rpc(FRT_RPCRequest * req)
{
    int32_t retval(-7);
    if (_target) {
        _target->InvokeSync(req, NEVER);
        if (req->GetErrorCode() == FRTE_NO_ERROR) {
            retval = (req->GetReturn()->GetValue(0)._intval32);
            LOG(debug, "rpc %s = %d", req->GetMethodName(), retval);
        } else {
            LOG(warning, "%s: error(%d): %s", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
        }
    } else {
        retval = -6;
    }
    return retval;
}

Session *
TransLogClient::findSession(const vespalib::string & domainName, int sessionId)
{
    SessionKey key(domainName, sessionId);
    SessionMap::iterator found(_sessions.find(key));
    Session * session((found != _sessions.end()) ? found->second : nullptr);
    return session;
}

void
TransLogClient::exportRPC(FRT_Supervisor & supervisor)
{
    FRT_ReflectionBuilder rb( & supervisor);

    //-- Visit Callbacks -----------------------------------------------------------
    rb.DefineMethod("visitCallback", "six", "i", FRT_METHOD(TransLogClient::visitCallbackRPC_hook), this);
    rb.MethodDesc("Will return data asked from a subscriber/visitor.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ParamDesc("session", "Session handle.");
    rb.ParamDesc("packet", "The data packet.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Non zero number indicates error.");

    //-- Visit Callbacks -----------------------------------------------------------
    rb.DefineMethod("eofCallback", "si", "i", FRT_METHOD(TransLogClient::eofCallbackRPC_hook), this);
    rb.MethodDesc("Will tell you that you are done with the visitor.");
    rb.ParamDesc("name", "The name of the domain.");
    rb.ParamDesc("session", "Session handle.");
    rb.ReturnDesc("result", "A resultcode(int) of the operation. Non zero number indicates error.");
}


void
TransLogClient::do_visitCallbackRPC(FRT_RPCRequest *req)
{
    uint32_t retval(uint32_t(-1));
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    int32_t sessionId(params[1]._intval32);
    LOG(spam, "visitCallback(%s, %d)(%d)", domainName, sessionId, params[2]._data._len);
    Session * session(findSession(domainName, sessionId));
    if (session != nullptr) {
        Packet packet(params[2]._data._buf, params[2]._data._len);
        retval = session->visit(packet);
    }
    ret.AddInt32(retval);
    LOG(debug, "visitCallback(%s, %d)=%d done", domainName, sessionId, retval);
}

void
TransLogClient::do_eofCallbackRPC(FRT_RPCRequest *req)
{
    uint32_t retval(uint32_t(-1));
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    int32_t sessionId(params[1]._intval32);
    LOG(debug, "eofCallback(%s, %d)", domainName, sessionId);
    Session * session(findSession(domainName, sessionId));
    if (session != nullptr) {
        session->eof();
        retval = 0;
    }
    ret.AddInt32(retval);
    LOG(debug, "eofCallback(%s, %d)=%d done", domainName, sessionId, retval);
}

void
TransLogClient::visitCallbackRPC_hook(FRT_RPCRequest *req)
{
    _executor->execute(std::make_unique<RpcTask>(req->Detach(), [this](FRT_RPCRequest *x){ do_visitCallbackRPC(x); }));
}

void
TransLogClient::eofCallbackRPC_hook(FRT_RPCRequest *req)
{
    _executor->execute(std::make_unique<RpcTask>(req->Detach(), [this](FRT_RPCRequest *x){ do_eofCallbackRPC(x); }));
}

}
