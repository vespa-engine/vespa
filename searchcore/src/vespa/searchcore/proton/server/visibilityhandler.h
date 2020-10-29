// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ifeedview.h"
#include "igetserialnum.h"
#include <vespa/searchcore/proton/common/icommitable.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/varholder.h>
#include <vespa/vespalib/util/time.h>
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
    void commit() override;
    void commitAndWait(ILidCommitState & unCommittedLidTracker) override;
    void commitAndWait(ILidCommitState &, uint32_t ) override;
    void commitAndWait(ILidCommitState &, const std::vector<uint32_t> & ) override;
private:
    bool startCommit(const std::lock_guard<std::mutex> &unused, bool force);
    void performCommit(bool force);
    void internalCommit(bool force);
    const IGetSerialNum  & _serial;
    IThreadingService    & _writeService;
    const FeedViewHolder & _feedView;
    SerialNum              _lastCommitSerialNum;
    std::mutex             _lock;
};

}
