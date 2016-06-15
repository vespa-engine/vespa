// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/ifeedview.h>
#include <vespa/searchcore/proton/server/icommitable.h>
#include <vespa/searchcore/proton/server/igetserialnum.h>
#include <vespa/searchcorespi/index/ithreadingservice.h>
#include <vespa/vespalib/util/varholder.h>

namespace proton {

/**
 * Handle commit of changes withing the allowance of visibilitydelay.
 * It will both handle background commit jobs and the necessary commit and wait for sequencing.
 **/
class VisibilityHandler : public ICommitable
{
    typedef vespalib::LockGuard       LockGuard;
    typedef fastos::TimeStamp         TimeStamp;
    using IThreadingService = searchcorespi::index::IThreadingService;
    typedef vespalib::ThreadExecutor  ThreadExecutor;
    typedef vespalib::Lock            Lock;
    typedef vespalib::VarHolder<IFeedView::SP> FeedViewHolder;
public:
    typedef search::SerialNum         SerialNum;
    VisibilityHandler(const IGetSerialNum &serial,
                      IThreadingService &threadingService,
                      const FeedViewHolder &feedView);
    void setVisibilityDelay(TimeStamp visibilityDelay) { _visibilityDelay = visibilityDelay; }
    TimeStamp getVisibilityDelay() const { return _visibilityDelay; } 
    void commit() override;
    virtual void commitAndWait() override;
private:
    bool startCommit(const LockGuard & unused);
    void performCommit();
    const IGetSerialNum  & _serial;
    IThreadingService    & _writeService;
    const FeedViewHolder & _feedView;
    TimeStamp              _visibilityDelay;
    SerialNum              _lastCommitSerialNum;
    Lock                   _lock;
};

}
