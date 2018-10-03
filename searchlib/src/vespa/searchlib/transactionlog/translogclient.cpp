// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "translogclient.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".translogclient");

using namespace std::chrono_literals;

VESPA_THREAD_STACK_TAG(translogclient_rpc_callback)

namespace search::transactionlog {

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
    ~RpcTask() {
        if (req != nullptr) {
            req->SetError(FRTE_RPC_METHOD_FAILED, "client has been shut down");
            req->Return();
        }
    }
};

}

using vespalib::LockGuard;

TransLogClient::TransLogClient(const vespalib::string & rpcTarget) :
    _executor(1, 128 * 1024, translogclient_rpc_callback),
    _rpcTarget(rpcTarget),
    _sessions(),
    _supervisor(std::make_unique<FRT_Supervisor>()),
    _target(NULL)
{
    reconnect();
    exportRPC(*_supervisor);
    _supervisor->Start();
}

TransLogClient::~TransLogClient()
{
    disconnect();
    _executor.shutdown().sync();
    _supervisor->ShutDown(true);
}

bool TransLogClient::reconnect()
{
    disconnect();
    _target = _supervisor->Get2WayTarget(_rpcTarget.c_str());
    return isConnected();
}

bool TransLogClient::isConnected() const {
    return (_target != NULL) && _target->IsValid();
}

void TransLogClient::disconnect()
{
    if (_target) {
        _target->SubRef();
    }
}

bool TransLogClient::create(const vespalib::string & domain)
{
    FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
    req->SetMethodName("createDomain");
    req->GetParams()->AddString(domain.c_str());
    int32_t retval(rpc(req));
    req->SubRef();
    return (retval == 0);
}

bool TransLogClient::remove(const vespalib::string & domain)
{
    FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
    req->SetMethodName("deleteDomain");
    req->GetParams()->AddString(domain.c_str());
    int32_t retval(rpc(req));
    req->SubRef();
    return (retval == 0);
}

TransLogClient::Session::UP TransLogClient::open(const vespalib::string & domain)
{
    Session::UP session;
    FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
    req->SetMethodName("openDomain");
    req->GetParams()->AddString(domain.c_str());
    int32_t retval(rpc(req));
    if (retval == 0) {
        session.reset(new Session(domain, *this));
    }
    req->SubRef();
    return session;
}

TransLogClient::Visitor::UP
TransLogClient::createVisitor(const vespalib::string & domain, TransLogClient::Session::Callback & callBack)
{
    return TransLogClient::Visitor::UP(new Visitor(domain, *this, callBack));
}

bool TransLogClient::listDomains(std::vector<vespalib::string> & dir)
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
    req->SubRef();
    return (retval == 0);
}

int32_t TransLogClient::rpc(FRT_RPCRequest * req)
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

TransLogClient::Session * TransLogClient::findSession(const vespalib::string & domainName, int sessionId)
{
    SessionKey key(domainName, sessionId);
    SessionMap::iterator found(_sessions.find(key));
    Session * session((found != _sessions.end()) ? found->second : NULL);
    return session;
}

void TransLogClient::exportRPC(FRT_Supervisor & supervisor)
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


void TransLogClient::do_visitCallbackRPC(FRT_RPCRequest *req)
{
    uint32_t retval(uint32_t(-1));
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    int32_t sessionId(params[1]._intval32);
    LOG(spam, "visitCallback(%s, %d)(%d)", domainName, sessionId, params[2]._data._len);
    Session * session(findSession(domainName, sessionId));
    if (session != NULL) {
        Packet packet(params[2]._data._buf, params[2]._data._len);
        retval = session->visit(packet);
    }
    ret.AddInt32(retval);
    LOG(debug, "visitCallback(%s, %d)=%d done", domainName, sessionId, retval);
}

void TransLogClient::do_eofCallbackRPC(FRT_RPCRequest *req)
{
    uint32_t retval(uint32_t(-1));
    FRT_Values & params = *req->GetParams();
    FRT_Values & ret    = *req->GetReturn();
    const char * domainName = params[0]._string._str;
    int32_t sessionId(params[1]._intval32);
    LOG(debug, "eofCallback(%s, %d)", domainName, sessionId);
    Session * session(findSession(domainName, sessionId));
    if (session != NULL) {
        session->eof();
        retval = 0;
    }
    ret.AddInt32(retval);
    LOG(debug, "eofCallback(%s, %d)=%d done", domainName, sessionId, retval);
}

void TransLogClient::visitCallbackRPC_hook(FRT_RPCRequest *req)
{
    _executor.execute(std::make_unique<RpcTask>(req->Detach(), [this](FRT_RPCRequest *x){ do_visitCallbackRPC(x); }));
}

void TransLogClient::eofCallbackRPC_hook(FRT_RPCRequest *req)
{
    _executor.execute(std::make_unique<RpcTask>(req->Detach(), [this](FRT_RPCRequest *x){ do_eofCallbackRPC(x); }));
}


TransLogClient::Session::Session(const vespalib::string & domain, TransLogClient & tlc) :
    _tlc(tlc),
    _domain(domain),
    _sessionId(0)
{
}

TransLogClient::Session::~Session()
{
    close();
    clear();
}

bool TransLogClient::Session::commit(const vespalib::ConstBufferRef & buf)
{
    bool retval(true);
    if (buf.size() != 0) {
        FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
        req->SetMethodName("domainCommit");
        req->GetParams()->AddString(_domain.c_str());
        req->GetParams()->AddData(buf.c_str(), buf.size());
        int retcode = _tlc.rpc(req);
        retval = (retcode == 0);
        if (retval) {
            req->SubRef();
        } else {
            vespalib::string msg;
            if (req->GetReturn() != 0) {
                msg = req->GetReturn()->GetValue(1)._string._str;
            } else {
                msg = vespalib::make_string("Clientside error %s: error(%d): %s", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
            }
            req->SubRef();
            throw std::runtime_error(vespalib::make_string("commit failed with code %d. server says: %s", retcode, msg.c_str()));
        }
    }
    return retval;
}

bool TransLogClient::Session::status(SerialNum & b, SerialNum & e, size_t & count)
{
    FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
    req->SetMethodName("domainStatus");
    req->GetParams()->AddString(_domain.c_str());
    int32_t retval(_tlc.rpc(req));
    if (retval == 0) {
        b = req->GetReturn()->GetValue(1)._intval64;
        e = req->GetReturn()->GetValue(2)._intval64;
        count = req->GetReturn()->GetValue(3)._intval64;
    }
    req->SubRef();
    return (retval == 0);
}

bool TransLogClient::Session::erase(const SerialNum & to)
{
    FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
    req->SetMethodName("domainPrune");
    req->GetParams()->AddString(_domain.c_str());
    req->GetParams()->AddInt64(to);
    int32_t retval(_tlc.rpc(req));
    req->SubRef();
    if (retval == 1) {
        LOG(warning, "Prune to %" PRIu64 " denied since there were active visitors in that area", to);
    }
    return (retval == 0);
}


bool
TransLogClient::Session::sync(const SerialNum &syncTo, SerialNum &syncedTo)
{
    FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
    req->SetMethodName("domainSync");
    FRT_Values & params = *req->GetParams();
    params.AddString(_domain.c_str());
    params.AddInt64(syncTo);
    int32_t retval(_tlc.rpc(req));
    if (retval == 0) {
        syncedTo = req->GetReturn()->GetValue(1)._intval64;
    }
    req->SubRef();
    return (retval == 0);
}


void TransLogClient::Session::clear()
{
    if (_sessionId > 0) {
        LockGuard guard(_tlc._lock);
        _tlc._sessions.erase(SessionKey(_domain, _sessionId));
    }
    _sessionId = 0;
}

int TransLogClient::SessionKey::cmp(const TransLogClient::SessionKey & b) const
{
    int diff(strcmp(_domain.c_str(), b._domain.c_str()));
    if (diff == 0) {
        diff = _sessionId - b._sessionId;
    }
    return diff;
}

TransLogClient::Visitor::Visitor(const vespalib::string & domain, TransLogClient & tlc, Callback & callBack) :
    Session(domain, tlc),
    _callback(callBack)
{
}

bool TransLogClient::Session::init(FRT_RPCRequest *req)
{
    int32_t retval(_tlc.rpc(req));
    req->SubRef();
    if (retval > 0) {
        clear();
        _sessionId = retval;
        SessionKey key(_domain, _sessionId);
        {
            LockGuard guard(_tlc._lock);
            _tlc._sessions[key] = this;
        }
        retval = run();
    }
    return (retval > 0);
}

bool TransLogClient::Visitor::visit(const SerialNum & from, const SerialNum & to)
{
    FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
    req->SetMethodName("domainVisit");
    req->GetParams()->AddString(_domain.c_str());
    req->GetParams()->AddInt64(from);
    req->GetParams()->AddInt64(to);
    return init(req);
}

bool TransLogClient::Session::run()
{
    FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
    req->SetMethodName("domainSessionRun");
    req->GetParams()->AddString(_domain.c_str());
    req->GetParams()->AddInt32(_sessionId);
    int32_t retval(_tlc.rpc(req));
    req->SubRef();
    return (retval == 0);
}

bool TransLogClient::Session::close()
{
    int retval(0);
    if (_sessionId > 0) {
        do {
            FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
            req->SetMethodName("domainSessionClose");
            req->GetParams()->AddString(_domain.c_str());
            req->GetParams()->AddInt32(_sessionId);
            if ( (retval = _tlc.rpc(req)) > 0) {
                std::this_thread::sleep_for(10ms);
            }
            req->SubRef();
        } while ( retval == 1 );
    }
    return (retval == 0);
}

TransLogClient::Visitor::~Visitor() = default;

}
