// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visible_map.h"
#include <vespa/fnet/task.h>

class FRT_RPCRequest;
class FRT_Supervisor;

namespace slobrok {

class MirrorFetch : public FNET_Task,
                    public VisibleMap::IUpdateListener
{
private:
    FRT_RPCRequest  *_req;
    VisibleMap    &_map;
    vespalib::GenCnt _gen;

public:
    MirrorFetch(const MirrorFetch &) = delete;
    MirrorFetch& operator=(const MirrorFetch &) = delete;

    MirrorFetch(FRT_Supervisor *orb, FRT_RPCRequest *req, VisibleMap &map, vespalib::GenCnt gen);
    ~MirrorFetch();

    void completeReq();
    void PerformTask() override;
    void updated(VisibleMap &map) override;
    void aborted(VisibleMap &map) override;
    void invoke(uint32_t msTimeout);
};

class IncrementalFetch : public FNET_Task,
                         public VisibleMap::IUpdateListener
{
private:
    FRT_RPCRequest  *_req;
    VisibleMap      &_map;
    vespalib::GenCnt _gen;

public:
    IncrementalFetch(const IncrementalFetch &) = delete;
    IncrementalFetch& operator=(const IncrementalFetch &) = delete;

    IncrementalFetch(FRT_Supervisor *orb, FRT_RPCRequest *req, VisibleMap &map, vespalib::GenCnt gen);
    ~IncrementalFetch();

    void completeReq();
    void PerformTask() override;
    void updated(VisibleMap &map) override;
    void aborted(VisibleMap &map) override;
    void invoke(uint32_t msTimeout);
};

} // namespace slobrok

