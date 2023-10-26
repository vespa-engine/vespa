// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcmirror.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.rpcmirror");

namespace slobrok {

IncrementalFetch::IncrementalFetch(FRT_Supervisor *orb,
                                   FRT_RPCRequest *req,
                                   ServiceMapHistory &smh,
                                   vespalib::GenCnt gen)
  : FNET_Task(orb->GetScheduler()),
    _req(req),
    _smh(smh),
    _gen(gen)
{ }

IncrementalFetch::~IncrementalFetch() { }

void IncrementalFetch::completeReq(MapDiff diff) {
    FRT_Values &dst = *_req->GetReturn();

    dst.AddInt32(diff.fromGen.getAsInt());

    size_t sz = diff.removed.size();
    FRT_StringValue *rem = dst.AddStringArray(sz);
    for (uint32_t i = 0; i < sz; ++i) {
        dst.SetString(&rem[i], diff.removed[i].c_str());
    }

    sz = diff.updated.size();
    FRT_StringValue *names = dst.AddStringArray(sz);
    FRT_StringValue *specs = dst.AddStringArray(sz);
    for (uint32_t i = 0; i < sz; ++i) {
        dst.SetString(&names[i],  diff.updated[i].name.c_str());
        dst.SetString(&specs[i],  diff.updated[i].spec.c_str());
    }

    dst.AddInt32(diff.toGen.getAsInt());

    LOG(debug, "mirrorFetch %p done (gen %d -> gen %d)",
        this, diff.fromGen.getAsInt(), diff.toGen.getAsInt());
    _req->Return();
}

void
IncrementalFetch::PerformTask()
{
    if (_smh.cancel(this)) {
        completeReq(MapDiff(_gen, {}, {}, _gen));
    }
}


void IncrementalFetch::handle(MapDiff diff) {
    Kill(); // unschedule timeout task
    completeReq(std::move(diff));
}

void
IncrementalFetch::invoke(uint32_t msTimeout)
{
    _req->Detach();
    LOG(debug, "IncrementalFetch %p invoked from %s (gen %d, timeout %d ms)",
        this, _req->GetConnection()->GetSpec(), _gen.getAsInt(), msTimeout);
    if (msTimeout > 10000)
        msTimeout = 10000;
    Schedule(msTimeout * 0.001);
    _smh.asyncGenerationDiff(this, _gen);
}

} // namespace slobrok

