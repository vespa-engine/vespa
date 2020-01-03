// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ifeedview.h"
#include "icommitable.h"
#include "igetserialnum.h"
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
    typedef vespalib::ThreadExecutor  ThreadExecutor;
    typedef vespalib::VarHolder<IFeedView::SP> FeedViewHolder;
public:
    typedef search::SerialNum         SerialNum;
    VisibilityHandler(const IGetSerialNum &serial,
                      IThreadingService &threadingService,
                      const FeedViewHolder &feedView);
    void setVisibilityDelay(vespalib::duration visibilityDelay) { _visibilityDelay = visibilityDelay; }
    vespalib::duration getVisibilityDelay() const { return _visibilityDelay; }
    bool hasVisibilityDelay() const { return _visibilityDelay != vespalib::duration::zero(); }
    void commit() override;
    void commitAndWait() override;
private:
    bool startCommit(const std::lock_guard<std::mutex> &unused, bool force);
    void performCommit(bool force);
    const IGetSerialNum  & _serial;
    IThreadingService    & _writeService;
    const FeedViewHolder & _feedView;
    vespalib::duration     _visibilityDelay;
    SerialNum              _lastCommitSerialNum;
    std::mutex             _lock;
};

}
