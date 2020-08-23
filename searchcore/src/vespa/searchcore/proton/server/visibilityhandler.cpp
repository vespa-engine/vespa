// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visibilityhandler.h"
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/closuretask.h>

using vespalib::makeTask;
using vespalib::makeClosure;

namespace proton {

VisibilityHandler::VisibilityHandler(const IGetSerialNum & serial,
                                     IThreadingService &writeService,
                                     const FeedViewHolder & feedView)
    : _serial(serial),
      _writeService(writeService),
      _feedView(feedView),
      _visibilityDelay(vespalib::duration::zero()),
      _lastCommitSerialNum(0),
      _lock()
{
}

VisibilityHandler::~VisibilityHandler() = default;

void
VisibilityHandler::internalCommit(bool force)
{
    if (_writeService.master().isCurrentThread()) {
        performCommit(force);
    } else {
        std::lock_guard<std::mutex> guard(_lock);
        bool wasCommitTaskSpawned = startCommit(guard, force);
        (void) wasCommitTaskSpawned;
    }
}
void
VisibilityHandler::commit()
{
    internalCommit(true);
}

void
VisibilityHandler::commitAndWait(IPendingLidTracker & unCommittedLidTracker)
{
    if (unCommittedLidTracker.areAnyInFlight()) {
        internalCommit(false);
        unCommittedLidTracker.waitForEmpty();
    }
}

void
VisibilityHandler::commitAndWait(IPendingLidTracker & unCommittedLidTracker, uint32_t lid) {
    if (unCommittedLidTracker.isInFlight(lid)) {
        internalCommit(false);
        unCommittedLidTracker.waitForConsumed(lid);
    }

}
void
VisibilityHandler::commitAndWait(IPendingLidTracker & unCommittedLidTracker, const std::vector<uint32_t> & lids) {
    if (unCommittedLidTracker.areAnyInFlight(lids)) {
        internalCommit(false);
        unCommittedLidTracker.waitForConsumed(lids);
    }
}

bool
VisibilityHandler::startCommit(const std::lock_guard<std::mutex> &unused, bool force)
{
    (void) unused;
    SerialNum current = _serial.getSerialNum();
    if ((current > _lastCommitSerialNum) || force) {
        _writeService.master().execute(makeTask(makeClosure(this,
             &VisibilityHandler::performCommit, force)));
        return true;
    }
    return false;
}

void
VisibilityHandler::performCommit(bool force)
{
    // Called by master thread
    SerialNum current = _serial.getSerialNum();
    if ((current > _lastCommitSerialNum) || force) {
        IFeedView::SP feedView(_feedView.get());
        feedView->forceCommit(current);
        _lastCommitSerialNum = current;
    }
}

}
