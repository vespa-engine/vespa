// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcmirror.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>
LOG_SETUP(".rpcmirror");

namespace slobrok {

MirrorFetch::MirrorFetch(FRT_Supervisor *orb,
                         FRT_RPCRequest *req,
                         VisibleMap &map,
                         vespalib::GenCnt gen)
    : FNET_Task(orb->GetScheduler()),
      _req(req),
      _map(map),
      _gen(gen)
{ }


MirrorFetch::~MirrorFetch() { }


void
MirrorFetch::completeReq()
{
    vespalib::GenCnt newgen = _map.genCnt();
    if (newgen == _gen) { // no change
        _req->GetReturn()->AddStringArray(0);
        _req->GetReturn()->AddStringArray(0);
    } else {
        std::vector<const NamedService *> rpcsrvlist = _map.allVisible();

        FRT_Values &dst = *_req->GetReturn();
        size_t sz = rpcsrvlist.size();
        FRT_StringValue *names  = dst.AddStringArray(sz);
        FRT_StringValue *specs  = dst.AddStringArray(sz);
        for (uint32_t i = 0; i < rpcsrvlist.size(); ++i) {
            dst.SetString(&names[i],  rpcsrvlist[i]->getName());
            dst.SetString(&specs[i],  rpcsrvlist[i]->getSpec());
        }
        if (sz > 0) {
            LOG(debug, "mirrorFetch %p -> %u, last [%s,%s]",
                this,
                (unsigned int)sz,
                dst[0]._string_array._pt[sz-1]._str,
                dst[1]._string_array._pt[sz-1]._str);
        } else {
            LOG(debug, "mirrorFetch %p -> 0 size", this);
        }
    }
    _req->GetReturn()->AddInt32(newgen.getAsInt());
    LOG(debug, "mirrorFetch %p done (gen %d -> gen %d)",
        this, _gen.getAsInt(), newgen.getAsInt());
    _req->Return();
}


void
MirrorFetch::PerformTask()
{
    // cancel update notification
    _map.removeUpdateListener(this);
    completeReq();
}


void
MirrorFetch::updated(VisibleMap &map)
{
    LOG_ASSERT(&map == &_map);
    (void) &map;
    // unschedule timeout task
    Unschedule();
    completeReq();
}


void
MirrorFetch::aborted(VisibleMap &map)
{
    LOG_ASSERT(&map == &_map);
    (void) &map;
    // unschedule timeout task
    Unschedule();
    _req->SetError(FRTE_RPC_METHOD_FAILED, "slobrok shutting down");
    _req->Return();
}


void
MirrorFetch::invoke(uint32_t msTimeout)
{
    _req->Detach();
    LOG(debug, "MirrorFetch %p invoked from %s (gen %d, timeout %d ms)",
        this, _req->GetConnection()->GetSpec(), _gen.getAsInt(), msTimeout);
    if (_map.genCnt() != _gen || msTimeout == 0) {
        completeReq();
    } else {
        _map.addUpdateListener(this); // register as update listener
        if (msTimeout > 10000)
            msTimeout = 10000;
        Schedule((double) msTimeout / 1000.0);
    }
}

IncrementalFetch::IncrementalFetch(FRT_Supervisor *orb,
                         FRT_RPCRequest *req,
                         VisibleMap &map,
                         vespalib::GenCnt gen)
    : FNET_Task(orb->GetScheduler()),
      _req(req),
      _map(map),
      _gen(gen)
{ }

IncrementalFetch::~IncrementalFetch() { }

void
IncrementalFetch::completeReq()
{
    vespalib::GenCnt newgen = _map.genCnt();
    VisibleMap::MapDiff diff;
    FRT_Values &dst = *_req->GetReturn();

    if (newgen == _gen) { // no change
        dst.AddInt32(_gen.getAsInt());
    } else if (_map.hasHistory(_gen)) {
        diff = _map.history(_gen);
        dst.AddInt32(_gen.getAsInt());
    } else {
        dst.AddInt32(0);
        diff.updated = _map.allVisible();
    }

    size_t sz = diff.removed.size();
    FRT_StringValue *rem    = dst.AddStringArray(sz);
    for (uint32_t i = 0; i < sz; ++i) {
        dst.SetString(&rem[i],  diff.removed[i].c_str());
    }

    sz = diff.updated.size();
    FRT_StringValue *names  = dst.AddStringArray(sz);
    FRT_StringValue *specs  = dst.AddStringArray(sz);
    for (uint32_t i = 0; i < sz; ++i) {
        dst.SetString(&names[i],  diff.updated[i]->getName());
        dst.SetString(&specs[i],  diff.updated[i]->getSpec());
    }

    dst.AddInt32(newgen.getAsInt());
    LOG(debug, "mirrorFetch %p done (gen %d -> gen %d)",
        this, _gen.getAsInt(), newgen.getAsInt());
    _req->Return();
}


void
IncrementalFetch::PerformTask()
{
    // cancel update notification
    _map.removeUpdateListener(this);
    completeReq();
}


void
IncrementalFetch::updated(VisibleMap &map)
{
    LOG_ASSERT(&map == &_map);
    (void) &map;
    // unschedule timeout task
    Unschedule();
    completeReq();
}


void
IncrementalFetch::aborted(VisibleMap &map)
{
    LOG_ASSERT(&map == &_map);
    (void) &map;
    // unschedule timeout task
    Unschedule();
    _req->SetError(FRTE_RPC_METHOD_FAILED, "slobrok shutting down");
    _req->Return();
}


void
IncrementalFetch::invoke(uint32_t msTimeout)
{
    _req->Detach();
    LOG(debug, "IncrementalFetch %p invoked from %s (gen %d, timeout %d ms)",
        this, _req->GetConnection()->GetSpec(), _gen.getAsInt(), msTimeout);
    if (_map.genCnt() != _gen || msTimeout == 0) {
        completeReq();
    } else {
        _map.addUpdateListener(this); // register as update listener
        if (msTimeout > 10000)
            msTimeout = 10000;
        Schedule((double) msTimeout / 1000.0);
    }
}

} // namespace slobrok

