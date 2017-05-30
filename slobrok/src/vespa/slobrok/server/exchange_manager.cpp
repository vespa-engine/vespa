// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exchange_manager.h"
#include "rpc_server_map.h"
#include "sbenv.h"
#include <vespa/fnet/frt/supervisor.h>

#include <vespa/log/log.h>
LOG_SETUP(".rpcserver");

namespace slobrok {

//-----------------------------------------------------------------------------

ExchangeManager::ExchangeManager(SBEnv &env, RpcServerMap &rpcsrvmap)
    : _partners(NULL),
      _env(env),
      _rpcsrvmanager(env._rpcsrvmanager),
      _rpcsrvmap(rpcsrvmap)
{
}


OkState
ExchangeManager::addPartner(const char *name, const char *spec)
{
    RemoteSlobrok *oldremote = _partners[name];
    if (oldremote != NULL) {
        // already a partner, should be OK
        if (strcmp(spec, oldremote->getSpec()) != 0) {
            return OkState(FRTE_RPC_METHOD_FAILED,
                           "name already partner with different spec");
        }
        // this is probably a good time to try connecting again
        if (! oldremote->isConnected()) {
            oldremote->tryConnect();
        }
        return OkState();
    }

    RemoteSlobrok *partner = new RemoteSlobrok(name, spec, *this);
    LOG_ASSERT(_partners.isSet(name) == false);
    _partners.set(name, partner);
    partner->tryConnect();
    return OkState();
}


void
ExchangeManager::removePartner(const char *name)
{
    // assuming checks already done
    RemoteSlobrok *oldremote = _partners.remove(name);
    LOG_ASSERT(oldremote != NULL);
    delete oldremote;
}


std::vector<std::string>
ExchangeManager::getPartnerList()
{
    std::vector<std::string> partnerList;
    HashMap<RemoteSlobrok *>::Iterator itr =  _partners.iterator();
    for (; itr.valid(); itr.next()) {
        partnerList.push_back(std::string(itr.value()->getSpec()));
    }
    return partnerList;
}


void
ExchangeManager::forwardRemove(const char *name, const char *spec)
{
    RegRpcSrvCommand remremhandler
        = RegRpcSrvCommand::makeRemRemCmd(_env, name, spec);
    WorkPackage *package = new WorkPackage(WorkPackage::OP_REMOVE,
                                           name, spec, *this,
                                           remremhandler);
    HashMap<RemoteSlobrok *>::Iterator it = _partners.iterator();
    while (it.valid()) {
        RemoteSlobrok *partner = it.value();
        package->addItem(partner);
        it.next();
    }
    package->expedite();
}

void
ExchangeManager::doAdd(const char *name, const char *spec,
                       RegRpcSrvCommand rdc)
{
    HashMap<RemoteSlobrok *>::Iterator it =
        _partners.iterator();

    WorkPackage *package =
        new WorkPackage(WorkPackage::OP_DOADD, name, spec, *this, rdc);

    while (it.valid()) {
        RemoteSlobrok *partner = it.value();
        package->addItem(partner);
        it.next();
    }
    package->expedite();
}


void
ExchangeManager::wantAdd(const char *name, const char *spec,
                         RegRpcSrvCommand rdc)
{
    WorkPackage *package =
        new WorkPackage(WorkPackage::OP_WANTADD, name, spec, *this, rdc);
    HashMap<RemoteSlobrok *>::Iterator it = _partners.iterator();
    while (it.valid()) {
        RemoteSlobrok *partner = it.value();
        package->addItem(partner);
        it.next();
    }
    package->expedite();
}


void
ExchangeManager::healthCheck()
{
    int i=0;
    HashMap<RemoteSlobrok *>::Iterator it = _partners.iterator();
    while (it.valid()) {
        RemoteSlobrok *partner = it.value();
        partner->healthCheck();
        it.next();
        i++;
    }
    LOG(debug, "ExchangeManager::healthCheck for %d partners", i);
}

//-----------------------------------------------------------------------------

ExchangeManager::WorkPackage::WorkItem::WorkItem(WorkPackage &pkg,
                                                 RemoteSlobrok *rem,
                                                 FRT_RPCRequest *req)
    : _pkg(pkg), _pendingReq(req), _remslob(rem)
{
}

void
ExchangeManager::WorkPackage::WorkItem::RequestDone(FRT_RPCRequest *req)
{
    bool denied = false;
    LOG_ASSERT(req == _pendingReq);
    FRT_Values &answer = *(req->GetReturn());

    if (!req->IsError() && strcmp(answer.GetTypeString(), "is") == 0) {
        if (answer[0]._intval32 != 0) {
            LOG(warning, "request denied: %s [%d]",
                answer[1]._string._str, answer[0]._intval32);
            denied = true;
        } else {
            LOG(spam, "request approved");
        }
    } else {
        LOG(warning, "error doing workitem: %s", req->GetErrorMessage());
        // XXX tell remslob?
    }
    req->SubRef();
    _pendingReq = NULL;
    _pkg.doneItem(denied);
}

void
ExchangeManager::WorkPackage::WorkItem::expedite()
{
    _remslob->invokeAsync(_pendingReq, 2.0, this);
}

ExchangeManager::WorkPackage::WorkItem::~WorkItem()
{
    if (_pendingReq != NULL) {
        _pendingReq->Abort();
        LOG_ASSERT(_pendingReq == NULL);
    }
}


ExchangeManager::WorkPackage::WorkPackage(op_type op,
                                          const char *name, const char *spec,
                                          ExchangeManager &exchanger,
                                          RegRpcSrvCommand donehandler)
    : _work(),
      _doneCnt(0),
      _numDenied(0),
      _donehandler(donehandler),
      _exchanger(exchanger),
      _optype(op),
      _name(name),
      _spec(spec)
{
}

ExchangeManager::WorkPackage::~WorkPackage()
{
    for (size_t i = 0; i < _work.size(); i++) {
        delete _work[i];
        _work[i] = NULL;
    }
}

void
ExchangeManager::WorkPackage::doneItem(bool denied)
{
    ++_doneCnt;
    if (denied) {
        ++_numDenied;
    }
    LOG(spam, "package done %d/%d, %d denied",
        (int)_doneCnt, (int)_work.size(), (int)_numDenied);
    if (_doneCnt == _work.size()) {
        if (_numDenied > 0) {
            _donehandler.doneHandler(OkState(_numDenied, "denied by remote"));
        } else {
            _donehandler.doneHandler(OkState());
        }
        delete this;
    }
}


void
ExchangeManager::WorkPackage::addItem(RemoteSlobrok *partner)
{
    if (! partner->isConnected()) {
        return;
    }
    FRT_RPCRequest *r = _exchanger._env.getSupervisor()->AllocRPCRequest();
    // XXX should recheck rpcsrvmap again
    if (_optype == OP_REMOVE) {
        r->SetMethodName("slobrok.internal.doRemove");
    } else if (_optype == OP_WANTADD) {
        r->SetMethodName("slobrok.internal.wantAdd");
    } else if (_optype == OP_DOADD) {
        r->SetMethodName("slobrok.internal.doAdd");
    }
    r->GetParams()->AddString(_exchanger._env.mySpec());
    r->GetParams()->AddString(_name.c_str());
    r->GetParams()->AddString(_spec.c_str());

    WorkItem *item = new WorkItem(*this, partner, r);
    _work.push_back(item);
    LOG(spam, "added %s(%s,%s,%s) for %s to workpackage",
        r->GetMethodName(), _exchanger._env.mySpec(),
        _name.c_str(), _spec.c_str(), partner->getName());
}


void
ExchangeManager::WorkPackage::expedite()
{
    size_t sz = _work.size();
    if (sz == 0) {
        // no remotes need doing.
        _donehandler.doneHandler(OkState());
        delete this;
        return;
    }
    for (size_t i = 0; i < sz; i++) {
        _work[i]->expedite();
        // note that on the last iteration
        // this object may be deleted if
        // the RPC fails synchronously.
    }
}

//-----------------------------------------------------------------------------


} // namespace slobrok
