// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "service_map_history.h"
#include <vespa/fnet/task.h>
#include <vespa/vespalib/util/gencnt.h>

class FRT_RPCRequest;
class FRT_Supervisor;

namespace slobrok {

class IncrementalFetch : public FNET_Task,
                         public ServiceMapHistory::DiffCompletionHandler
{
private:
    FRT_RPCRequest *_req;
    ServiceMapHistory &_smh;
    vespalib::GenCnt _gen;

public:
    IncrementalFetch(const IncrementalFetch &) = delete;
    IncrementalFetch& operator=(const IncrementalFetch &) = delete;

    IncrementalFetch(FRT_Supervisor *orb, FRT_RPCRequest *req, ServiceMapHistory &smh, vespalib::GenCnt gen);
    ~IncrementalFetch();

    void completeReq(MapDiff diff);
    void PerformTask() override;
    void handle(MapDiff diff) override;
    void invoke(uint32_t msTimeout);
};

} // namespace slobrok

