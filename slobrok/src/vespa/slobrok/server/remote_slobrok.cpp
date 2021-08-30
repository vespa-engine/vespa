// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "remote_slobrok.h"
#include "rpc_server_map.h"
#include "rpc_server_manager.h"
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
      _rpcsrvmanager(manager.rpcServerManager()),
      _remote(nullptr),
      _serviceMapMirror(),
      _rpcserver(name, spec, *this),
      _reconnecter(getSupervisor()->GetScheduler(), *this),
      _failCnt(0),
      _consensusSubscription(MapSubscription::subscribe(_serviceMapMirror, _exchanger.env().consensusMap())),
      _remAddPeerReq(nullptr),
      _remListReq(nullptr),
      _remAddReq(nullptr),
      _remRemReq(nullptr),
      _remFetchReq(nullptr),
      _pending()
{
    _rpcserver.healthCheck();
}

void RemoteSlobrok::shutdown() {
    _reconnecter.disable();

    _pending.clear();

    if (_remote != nullptr) {
        _remote->SubRef();
        _remote = nullptr;
    }

    if (_remFetchReq != nullptr) {
        _remFetchReq->Abort();
    }
    if (_remAddPeerReq != nullptr) {
        _remAddPeerReq->Abort();
    }
    if (_remListReq != nullptr) {
        _remListReq->Abort();
    }
    if (_remAddReq != nullptr) {
        _remAddReq->Abort();
    }
    if (_remRemReq != nullptr) {
        _remRemReq->Abort();
    }
    _serviceMapMirror.clear();
}

RemoteSlobrok::~RemoteSlobrok() {
    shutdown();
    // _rpcserver destructor called automatically
}

void
RemoteSlobrok::doPending()
{
    if (_remAddReq != nullptr) return;
    if (_remRemReq != nullptr) return;

    if (_remote == nullptr) return;

    if ( ! _pending.empty() ) {
        std::unique_ptr<NamedService> todo = std::move(_pending.front());
        _pending.pop_front();

        const NamedService *rpcsrv = _exchanger.rpcServerMap().lookup(todo->getName());

        if (rpcsrv == nullptr) {
            _remRemReq = getSupervisor()->AllocRPCRequest();
            _remRemReq->SetMethodName("slobrok.internal.doRemove");
            _remRemReq->GetParams()->AddString(_exchanger.env().mySpec().c_str());
            _remRemReq->GetParams()->AddString(todo->getName().c_str());
            _remRemReq->GetParams()->AddString(todo->getSpec().c_str());
            _remote->InvokeAsync(_remRemReq, 2.0, this);
        } else {
            _remAddReq = getSupervisor()->AllocRPCRequest();
            _remAddReq->SetMethodName("slobrok.internal.doAdd");
            _remAddReq->GetParams()->AddString(_exchanger.env().mySpec().c_str());
            _remAddReq->GetParams()->AddString(todo->getName().c_str());
            _remAddReq->GetParams()->AddString(rpcsrv->getSpec().c_str());
            _remote->InvokeAsync(_remAddReq, 2.0, this);
        }
        // XXX should save this and pick up on RequestDone()
    }
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
    _remFetchReq->SubRef();
    _remFetchReq = nullptr;
    if (success) {
        maybeStartFetch();
    }
}

    

void
RemoteSlobrok::pushMine()
{
    // all mine
    std::vector<const NamedService *> mine = _exchanger.rpcServerMap().allManaged();
    while (mine.size() > 0) {
        const NamedService *now = mine.back();
        mine.pop_back();
        _pending.push_back(std::make_unique<NamedService>(now->getName(), now->getSpec()));
    }
    doPending();
}

void
RemoteSlobrok::RequestDone(FRT_RPCRequest *req)
{
    if (req == _remFetchReq) {
        handleFetchResult();
        return;
    }
    FRT_Values &answer = *(req->GetReturn());
    if (req == _remAddPeerReq) {
        // handle response after asking remote slobrok to add me as a peer:
        if (req->IsError()) {
            FRT_Values &args = *req->GetParams();
            const char *myname = args[0]._string._str;
            const char *myspec = args[1]._string._str;
            LOG(info, "addPeer(%s, %s) on remote slobrok %s at %s: %s",
                myname, myspec, getName().c_str(), getSpec().c_str(), req->GetErrorMessage());
            req->SubRef();
            _remAddPeerReq = nullptr;
            goto retrylater;
        }
        req->SubRef();
        _remAddPeerReq = nullptr;
        // next step is to ask the remote to send its list of managed names:
        LOG_ASSERT(_remListReq == nullptr);
        _remListReq = getSupervisor()->AllocRPCRequest();
        _remListReq->SetMethodName("slobrok.internal.listManagedRpcServers");
        if (_remote != nullptr) {
            _remote->InvokeAsync(_remListReq, 3.0, this);
        }
        // when _remListReq is returned, our managed list is added
    } else if (req == _remListReq) {
        // handle the list sent from the remote:
        if (req->IsError()
            || strcmp(answer.GetTypeString(), "SS") != 0)
        {
            LOG(error, "error listing remote slobrok %s at %s: %s",
                getName().c_str(), getSpec().c_str(), req->GetErrorMessage());
            req->SubRef();
            _remListReq = nullptr;
            goto retrylater;
        }
        uint32_t numNames = answer.GetValue(0)._string_array._len;
        uint32_t numSpecs = answer.GetValue(1)._string_array._len;

        if (numNames != numSpecs) {
            LOG(error, "inconsistent array lengths from %s at %s", getName().c_str(), getSpec().c_str());
            req->SubRef();
            _remListReq = nullptr;
            goto retrylater;
        }
        FRT_StringValue *names = answer.GetValue(0)._string_array._pt;
        FRT_StringValue *specs = answer.GetValue(1)._string_array._pt;

        for (uint32_t idx = 0; idx < numNames; idx++) {
            _rpcsrvmanager.addRemote(names[idx]._str, specs[idx]._str);
        }
        req->SubRef();
        _remListReq = nullptr;

        // next step is to push the ones I own:
        maybeStartFetch();
        maybePushMine();
    } else if (req == _remAddReq) {
        // handle response after pushing some name that we managed:
        if (req->IsError() && (req->GetErrorCode() == FRTE_RPC_CONNECTION ||
                               req->GetErrorCode() == FRTE_RPC_TIMEOUT))
        {
            LOG(error, "connection error adding to remote slobrok: %s", req->GetErrorMessage());
            req->SubRef();
            _remAddReq = nullptr;
            goto retrylater;
        }
        if (req->IsError()) {
            FRT_Values &args = *req->GetParams();
            const char *rpcsrvname     = args[1]._string._str;
            const char *rpcsrvspec     = args[2]._string._str;
            LOG(warning, "error adding [%s -> %s] to remote slobrok: %s",
                rpcsrvname, rpcsrvspec, req->GetErrorMessage());
            _rpcsrvmanager.removeLocal(rpcsrvname, rpcsrvspec);
        }
        req->SubRef();
        _remAddReq = nullptr;
        doPending();
    } else if (req == _remRemReq) {
        // handle response after pushing some remove we had pending:
        if (req->IsError() && (req->GetErrorCode() == FRTE_RPC_CONNECTION ||
                               req->GetErrorCode() == FRTE_RPC_TIMEOUT))
        {
            LOG(error, "connection error adding to remote slobrok: %s", req->GetErrorMessage());
            req->SubRef();
            _remRemReq = nullptr;
            goto retrylater;
        }
        if (req->IsError()) {
            LOG(warning, "error removing on remote slobrok: %s", req->GetErrorMessage());
        }
        req->SubRef();
        _remRemReq = nullptr;
        doPending();
    } else {
        LOG(error, "got unknown request back in RequestDone()");
        LOG_ASSERT(req == nullptr);
    }

    return;
 retrylater:
    fail();
    return;
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
        _remote->SubRef();
        _remote = nullptr;
    }
    // schedule reconnect attempt
    _reconnecter.scheduleTryConnect();
}


void
RemoteSlobrok::maybePushMine()
{
    if (_remote != nullptr &&
        _remAddPeerReq == nullptr &&
        _remListReq == nullptr &&
        _remAddReq == nullptr &&
        _remRemReq == nullptr)
    {
        LOG(debug, "spamming remote at %s with my names", getName().c_str());
        pushMine();
    } else {
        LOG(debug, "not pushing mine, as we have: remote %p r.a.p.r=%p r.l.r=%p r.a.r=%p r.r.r=%p",
		_remote, _remAddPeerReq, _remListReq, _remAddReq, _remRemReq);
    }
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

    // at this point, we will do (in sequence):
    // ask peer to connect to us too;
    // ask peer for its list of managed rpcservers, adding to our database
    // add our managed rpcserver on peer
    // any failure will cause disconnect and retry.

    _remAddPeerReq = getSupervisor()->AllocRPCRequest();
    _remAddPeerReq->SetMethodName("slobrok.admin.addPeer");
    _remAddPeerReq->GetParams()->AddString(_exchanger.env().mySpec().c_str());
    _remAddPeerReq->GetParams()->AddString(_exchanger.env().mySpec().c_str());
    _remote->InvokeAsync(_remAddPeerReq, 3.0, this);
    // when _remAddPeerReq is returned, our managed list is added via doAdd()
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
