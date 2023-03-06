// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "client_session.h"
#include "translogclient.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".translog.client_session");

using namespace std::chrono_literals;

namespace search::transactionlog::client {

SessionKey::SessionKey(const vespalib::string & domain, int sessionId)
    : _domain(domain),
      _sessionId(sessionId)
{ }
SessionKey::~SessionKey() = default;

int
SessionKey::cmp(const SessionKey & b) const
{
    int diff(strcmp(_domain.c_str(), b._domain.c_str()));
    if (diff == 0) {
        diff = _sessionId - b._sessionId;
    }
    return diff;
}

Session::Session(const vespalib::string & domain, TransLogClient & tlc)
    : _tlc(tlc),
      _domain(domain),
      _sessionId(0)
{
}

Session::~Session()
{
    close();
    clear();
}

bool
Session::commit(const vespalib::ConstBufferRef & buf)
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
            req->internal_subref();
        } else {
            vespalib::string msg;
            if (req->GetReturn() != nullptr) {
                msg = req->GetReturn()->GetValue(1)._string._str;
            } else {
                msg = vespalib::make_string("Clientside error %s: error(%d): %s", req->GetMethodName(), req->GetErrorCode(), req->GetErrorMessage());
            }
            req->internal_subref();
            throw std::runtime_error(vespalib::make_string("commit failed with code %d. server says: %s", retcode, msg.c_str()));
        }
    }
    return retval;
}

bool
Session::status(SerialNum & b, SerialNum & e, size_t & count)
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
    req->internal_subref();
    return (retval == 0);
}

bool
Session::erase(const SerialNum & to)
{
    FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
    req->SetMethodName("domainPrune");
    req->GetParams()->AddString(_domain.c_str());
    req->GetParams()->AddInt64(to);
    int32_t retval(_tlc.rpc(req));
    req->internal_subref();
    if (retval == 1) {
        LOG(warning, "Prune to %" PRIu64 " denied since there were active visitors in that area", to);
    }
    return (retval == 0);
}


bool
Session::sync(const SerialNum &syncTo, SerialNum &syncedTo)
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
    req->internal_subref();
    return (retval == 0);
}


void
Session::clear()
{
    if (_sessionId > 0) {
        std::lock_guard guard(_tlc._lock);
        _tlc._sessions.erase(SessionKey(_domain, _sessionId));
    }
    _sessionId = 0;
}

Visitor::Visitor(const vespalib::string & domain, TransLogClient & tlc, Callback & callBack) :
    Session(domain, tlc),
    _callback(callBack)
{
}

bool
Session::init(FRT_RPCRequest *req)
{
    int32_t retval(_tlc.rpc(req));
    req->internal_subref();
    if (retval > 0) {
        clear();
        _sessionId = retval;
        SessionKey key(_domain, _sessionId);
        {
            std::lock_guard guard(_tlc._lock);
            _tlc._sessions[key] = this;
        }
        retval = run();
    }
    return (retval > 0);
}

bool
Visitor::visit(const SerialNum & from, const SerialNum & to)
{
    FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
    req->SetMethodName("domainVisit");
    req->GetParams()->AddString(_domain.c_str());
    req->GetParams()->AddInt64(from);
    req->GetParams()->AddInt64(to);
    return init(req);
}

bool
Session::run()
{
    FRT_RPCRequest *req = _tlc._supervisor->AllocRPCRequest();
    req->SetMethodName("domainSessionRun");
    req->GetParams()->AddString(_domain.c_str());
    req->GetParams()->AddInt32(_sessionId);
    int32_t retval(_tlc.rpc(req));
    req->internal_subref();
    return (retval == 0);
}

bool
Session::close()
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
            req->internal_subref();
        } while ( retval == 1 );
    }
    return (retval == 0);
}

Visitor::~Visitor() = default;

}
