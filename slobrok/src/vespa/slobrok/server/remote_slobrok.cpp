// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "remote_slobrok.h"
#include "exchange_manager.h"
#include "sbenv.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.remote_slobrok");

namespace slobrok {

//-----------------------------------------------------------------------------

RemoteSlobrok::RemoteSlobrok(const std::string &name, const std::string &spec,
                             ExchangeManager &manager)
    : _exchanger(manager),
      _remote(nullptr),
      _serviceMapMirror(),
      _rpcserver(name, spec, *this),
      _reconnecter(getSupervisor()->GetScheduler(), *this),
      _failCnt(0),
      _consensusSubscription(MapSubscription::subscribe(_serviceMapMirror, _exchanger.env().consensusMap())),
      _remAddPeerReq(nullptr),
      _remFetchReq(nullptr)
{
    _rpcserver.healthCheck();
}

void RemoteSlobrok::shutdown() {
    _reconnecter.disable();

    if (_remote != nullptr) {
        _remote->internal_subref();
        _remote = nullptr;
    }

    if (_remFetchReq != nullptr) {
        _remFetchReq->Abort();
    }
    if (_remAddPeerReq != nullptr) {
        _remAddPeerReq->Abort();
    }
    _serviceMapMirror.clear();
}

RemoteSlobrok::~RemoteSlobrok() {
    shutdown();
    // _rpcserver destructor called automatically
}

void RemoteSlobrok::maybeStartFetch() {
    if (_remFetchReq != nullptr) return;
    if (_remote == nullptr) return;
    _remFetchReq = getSupervisor()->AllocRPCRequest();
    _remFetchReq->SetMethodName("slobrok.internal.fetchLocalView");
    _remFetchReq->GetParams()->AddInt32(_serviceMapMirror.currentGeneration().getAsInt());
    _remFetchReq->GetParams()->AddInt32(5000);
    _remote->InvokeAsync(_remFetchReq, 15.0, this);
}

void RemoteSlobrok::handleFetchResult() {
    LOG_ASSERT(_remFetchReq != nullptr);
    bool success = true;
    if (_remFetchReq->CheckReturnTypes("iSSSi")) {
        FRT_Values &answer = *(_remFetchReq->GetReturn());

        uint32_t diff_from = answer[0]._intval32;
        uint32_t numRemove = answer[1]._string_array._len;
        FRT_StringValue *r = answer[1]._string_array._pt;
        uint32_t numNames  = answer[2]._string_array._len;
        FRT_StringValue *n = answer[2]._string_array._pt;
        uint32_t numSpecs  = answer[3]._string_array._len;
        FRT_StringValue *s = answer[3]._string_array._pt;
        uint32_t diff_to   = answer[4]._intval32;

        std::vector<vespalib::string> removed;
        for (uint32_t idx = 0; idx < numRemove; ++idx) {
            removed.emplace_back(r[idx]._str);
        }
        ServiceMappingList updated;
        if (numNames == numSpecs) {
            for (uint32_t idx = 0; idx < numNames; ++idx) {
                updated.emplace_back(n[idx]._str, s[idx]._str);
            }
        } else {
            diff_from = 0;
            diff_to = 0;
            success = false;
        }
        MapDiff diff(diff_from, std::move(removed), std::move(updated), diff_to);
        if (diff_from == 0) {
            _serviceMapMirror.clear();
            _serviceMapMirror.apply(std::move(diff));
        } else if (diff_from == _serviceMapMirror.currentGeneration().getAsInt()) {
            _serviceMapMirror.apply(std::move(diff));
        } else {
            _serviceMapMirror.clear();
            success = false;
        }
    } else {
        if (_remFetchReq->GetErrorCode() == FRTE_RPC_NO_SUCH_METHOD) {
            LOG(debug, "partner slobrok too old - not mirroring");
        } else {
            LOG(debug, "fetchLocalView() failed with partner %s: %s",
                getName().c_str(), _remFetchReq->GetErrorMessage());            
            fail();
        }
        _serviceMapMirror.clear();
        success = false;
    }
    _remFetchReq->internal_subref();
    _remFetchReq = nullptr;
    if (success) {
        maybeStartFetch();
    }
}

void
RemoteSlobrok::RequestDone(FRT_RPCRequest *req)
{
    if (req == _remFetchReq) {
        handleFetchResult();
        return;
    }
    if (req == _remAddPeerReq) {
        // handle response after asking remote slobrok to add me as a peer:
        if (req->IsError()) {
            FRT_Values &args = *req->GetParams();
            const char *myname = args[0]._string._str;
            const char *myspec = args[1]._string._str;
            LOG(info, "addPeer(%s, %s) on remote slobrok %s at %s: %s",
                myname, myspec, getName().c_str(), getSpec().c_str(), req->GetErrorMessage());
            req->internal_subref();
            _remAddPeerReq = nullptr;
            fail();
            return;
        }
        req->internal_subref();
        _remAddPeerReq = nullptr;
    } else {
        LOG(error, "got unknown request back in RequestDone()");
        LOG_ASSERT(req == nullptr);
    }
}


void
RemoteSlobrok::notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg)
{
    if (++_failCnt > 10) {
        LOG(warning, "remote location broker at %s failed: %s",
            rpcsrv->getSpec().c_str(), errmsg.c_str());
    } else {
        LOG(debug, "remote location broker at %s failed: %s",
            rpcsrv->getSpec().c_str(), errmsg.c_str());
    }
    LOG_ASSERT(rpcsrv == &_rpcserver);
    fail();
}


void
RemoteSlobrok::fail()
{
    // disconnect
    if (_remote != nullptr) {
        _remote->internal_subref();
        _remote = nullptr;
    }
    // schedule reconnect attempt
    _reconnecter.scheduleTryConnect();
}

void
RemoteSlobrok::notifyOkRpcSrv(ManagedRpcServer *rpcsrv)
{
    LOG_ASSERT(rpcsrv == &_rpcserver);
    (void) rpcsrv;

    // connection was OK, so disable any pending reconnect
    _reconnecter.disable();

    if (_remote != nullptr) {
        maybeStartFetch();
        // the rest here should only be done on first notifyOk
        return;
    }
    _remote = getSupervisor()->GetTarget(getSpec().c_str());
    maybeStartFetch();

    // at this point, we will ask peer to connect to us too;
    // any failure will cause disconnect and retry.

    _remAddPeerReq = getSupervisor()->AllocRPCRequest();
    _remAddPeerReq->SetMethodName("slobrok.admin.addPeer");
    _remAddPeerReq->GetParams()->AddString(_exchanger.env().mySpec().c_str());
    _remAddPeerReq->GetParams()->AddString(_exchanger.env().mySpec().c_str());
    _remote->InvokeAsync(_remAddPeerReq, 3.0, this);
}

void
RemoteSlobrok::tryConnect()
{
    _rpcserver.healthCheck();
}

FRT_Supervisor *
RemoteSlobrok::getSupervisor()
{
    return _exchanger.env().getSupervisor();
}

//-----------------------------------------------------------------------------

RemoteSlobrok::Reconnecter::Reconnecter(FNET_Scheduler *sched,
                                        RemoteSlobrok &owner)
    : FNET_Task(sched),
      _waittime(13),
      _owner(owner)
{
}

RemoteSlobrok::Reconnecter::~Reconnecter()
{
    Kill();
}

void
RemoteSlobrok::Reconnecter::scheduleTryConnect()
{
    if (_waittime < 60)
        ++_waittime;
    Schedule(_waittime + (random() & 255)/100.0);

}

void
RemoteSlobrok::Reconnecter::disable()
{
    // called when connection OK
    Unschedule();
    _waittime = 13;
}

void
RemoteSlobrok::Reconnecter::PerformTask()
{
    _owner.tryConnect();
}

void
RemoteSlobrok::invokeAsync(FRT_RPCRequest *req,
                           double timeout,
                           FRT_IRequestWait *rwaiter)
{
    LOG_ASSERT(isConnected());
    _remote->InvokeAsync(req, timeout, rwaiter);
}


//-----------------------------------------------------------------------------


} // namespace slobrok
