// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exchange_manager.h"
#include "sbenv.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.exchange_manager");

namespace slobrok {

namespace {
vespalib::steady_time now() {
    return vespalib::steady_clock::now();
}
}

//-----------------------------------------------------------------------------

ExchangeManager::ExchangeManager(SBEnv &env)
    : _partners(),
      _env(env),
      _lastFullConsensusTime(now())
{
}

ExchangeManager::~ExchangeManager() = default;

OkState
ExchangeManager::addPartner(const std::string & spec)
{
    if (RemoteSlobrok *oldremote = lookupPartner(spec)) {
        // already a partner, should be OK
        if (spec != oldremote->getSpec()) {
            return OkState(FRTE_RPC_METHOD_FAILED, "name already partner with different spec");
        }
        // this is probably a good time to try connecting again
        if (! oldremote->isConnected()) {
            oldremote->tryConnect();
        }
        return OkState();
    }
    auto [ it, wasNew ] = _partners.emplace(spec, std::make_unique<RemoteSlobrok>(spec, spec, *this));
    LOG_ASSERT(wasNew);
    RemoteSlobrok & partner = *it->second;
    partner.tryConnect();
    return OkState();
}

void
ExchangeManager::removePartner(const std::string & name)
{
    // assuming checks already done
    auto oldremote = std::move(_partners[name]);
    LOG_ASSERT(oldremote);
    _partners.erase(name);
    oldremote->shutdown();
}

std::vector<std::string>
ExchangeManager::getPartnerList()
{
    std::vector<std::string> partnerList;
    for (const auto & entry : _partners) {
        partnerList.push_back(entry.second->getSpec());
    }
    return partnerList;
}


void
ExchangeManager::forwardRemove(const std::string & name, const std::string & spec)
{
    WorkPackage *package = new WorkPackage(WorkPackage::OP_REMOVE, ServiceMapping{name, spec}, *this);
    for (const auto & entry : _partners) {
        package->addItem(entry.second.get());
    }
    package->expedite();
}


RemoteSlobrok *
ExchangeManager::lookupPartner(const std::string & name) const {
    auto found = _partners.find(name);
    return (found == _partners.end()) ? nullptr : found->second.get();
}

vespalib::string
ExchangeManager::diffLists(const ServiceMappingList &lhs, const ServiceMappingList &rhs)
{
    using namespace vespalib;
    vespalib::string result;
    auto visitor = overload
        {
            [&result](visit_ranges_first, const auto &m) {
                result.append("\nmissing: ").append(m.name).append("->").append(m.spec);
            },
            [&result](visit_ranges_second, const auto &m) {
                result.append("\nextra: ").append(m.name).append("->").append(m.spec);
            },
            [](visit_ranges_both, const auto &, const auto &) {}
        };
    visit_ranges(visitor, lhs.begin(), lhs.end(), rhs.begin(), rhs.end());
    return result;
}

void
ExchangeManager::healthCheck()
{
    bool someBad = false;
    auto newWorldList = env().consensusMap().currentConsensus();
    for (const auto & [ name, partner ] : _partners) {
        partner->maybeStartFetch();
        auto remoteList = partner->remoteMap().allMappings();
        // 0 is expected (when remote is down)
        if (remoteList.size() != 0) {
            vespalib::string diff = diffLists(newWorldList, remoteList);
            if (! diff.empty()) {
                LOG(warning, "Peer slobrok at %s may have problems, differences from consensus map: %s",
                    partner->getName().c_str(), diff.c_str());
                someBad = true;
            }
        }
    }
    if (someBad) {
        _env.setConsensusTime(vespalib::to_s(now() - _lastFullConsensusTime));
    } else {
        _lastFullConsensusTime = now();
        _env.setConsensusTime(0);
    }
    LOG(debug, "ExchangeManager::healthCheck for %ld partners", _partners.size());
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
            LOG(warning, "request denied: %s [%d]", answer[1]._string._str, answer[0]._intval32);
            denied = true;
        } else {
            LOG(spam, "request approved");
        }
    } else {
        LOG(warning, "error doing workitem: %s", req->GetErrorMessage());
        // XXX tell remslob?
    }
    req->internal_subref();
    _pendingReq = nullptr;
    _pkg.doneItem(denied);
}

void
ExchangeManager::WorkPackage::WorkItem::expedite()
{
    _remslob->invokeAsync(_pendingReq, 2.0, this);
}

ExchangeManager::WorkPackage::WorkItem::~WorkItem()
{
    if (_pendingReq != nullptr) {
        _pendingReq->Abort();
        // _pendingReq cleared by RequestDone Method
        LOG_ASSERT(_pendingReq == nullptr);
    }
}


ExchangeManager::WorkPackage::WorkPackage(op_type op, const ServiceMapping &mapping, ExchangeManager &exchanger)
    : _work(),
      _doneCnt(0),
      _numDenied(0),
      _exchanger(exchanger),
      _mapping(mapping),
      _optype(op)
{
}

ExchangeManager::WorkPackage::~WorkPackage() = default;

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
            LOG(debug, "work package [%s->%s]: %zd/%zd denied by remote",
                _mapping.name.c_str(), _mapping.spec.c_str(),
                _numDenied, _doneCnt);
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
    const char *name_p = _mapping.name.c_str();
    const char *spec_p = _mapping.spec.c_str();

    FRT_RPCRequest *r = _exchanger._env.getSupervisor()->AllocRPCRequest();
    LOG_ASSERT(_optype == OP_REMOVE);
    r->SetMethodName("slobrok.internal.doRemove");
    r->GetParams()->AddString(_exchanger._env.mySpec().c_str());
    r->GetParams()->AddString(name_p);
    r->GetParams()->AddString(spec_p);

    _work.push_back(std::make_unique<WorkItem>(*this, partner, r));
    LOG(spam, "added %s(%s,%s,%s) for %s to workpackage",
        r->GetMethodName(), _exchanger._env.mySpec().c_str(),
        name_p, spec_p, partner->getName().c_str());
}


void
ExchangeManager::WorkPackage::expedite()
{
    size_t sz = _work.size();
    if (sz == 0) {
        // no remotes need doing.
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
