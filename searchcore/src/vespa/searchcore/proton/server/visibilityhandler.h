// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ifeedview.h"
#include "igetserialnum.h"
#include <vespa/searchcore/proton/common/icommitable.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/varholder.h>
#include <mutex>

namespace proton {

/**
 * Handle commit of changes withing the allowance of visibilitydelay.
 * It will both handle background commit jobs and the necessary commit and wait for sequencing.
 **/
class VisibilityHandler : public ICommitable
{
    using IThreadingService = searchcorespi::index::IThreadingService;
    using FeedViewHolder = vespalib::VarHolder<IFeedView::SP>;
public:
    typedef search::SerialNum         SerialNum;
    VisibilityHandler(const IGetSerialNum &serial,
                      IThreadingService &threadingService,
                      const FeedViewHolder &feedView);
    ~VisibilityHandler() override;
    void setVisibilityDelay(vespalib::duration visibilityDelay) { _visibilityDelay = visibilityDelay; }
    vespalib::duration getVisibilityDelay() const { return _visibilityDelay; }
    bool hasVisibilityDelay() const { return _visibilityDelay != vespalib::duration::zero(); }
    void commit() override;
    void commitAndWait(IPendingLidTracker & unCommittedLidTracker) override;
    void commitAndWait(IPendingLidTracker &, uint32_t ) override;
    void commitAndWait(IPendingLidTracker &, const std::vector<uint32_t> & ) override;
private:
    bool startCommit(const std::lock_guard<std::mutex> &unused, bool force);
    void performCommit(bool force);
    void internalCommit(bool force);
    const IGetSerialNum  & _serial;
    IThreadingService    & _writeService;
    const FeedViewHolder & _feedView;
    vespalib::duration     _visibilityDelay;
    SerialNum              _lastCommitSerialNum;
    std::mutex             _lock;
};

}
