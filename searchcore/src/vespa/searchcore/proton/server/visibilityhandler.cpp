// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visibilityhandler.h"
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>

using vespalib::makeLambdaTask;

namespace proton {

VisibilityHandler::VisibilityHandler(const IGetSerialNum & serial,
                                     IThreadingService &writeService,
                                     const FeedViewHolder & feedView)
    : _serial(serial),
      _writeService(writeService),
      _feedView(feedView),
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
VisibilityHandler::commitAndWait(ILidCommitState & unCommittedLidTracker)
{
    ILidCommitState::State state = unCommittedLidTracker.getState();
    if (state == ILidCommitState::State::NEED_COMMIT) {
        internalCommit(false);
    }
    if (state != ILidCommitState::State::COMPLETED) {
        unCommittedLidTracker.waitComplete();
    }
}

void
VisibilityHandler::commitAndWait(ILidCommitState & unCommittedLidTracker, uint32_t lid) {
    ILidCommitState::State state = unCommittedLidTracker.getState(lid);
    if (state == ILidCommitState::State::NEED_COMMIT) {
        internalCommit(false);
    }
    if (state != ILidCommitState::State::COMPLETED) {
        unCommittedLidTracker.waitComplete(lid);
    }
}
void
VisibilityHandler::commitAndWait(ILidCommitState & unCommittedLidTracker, const std::vector<uint32_t> & lids) {
    ILidCommitState::State state = unCommittedLidTracker.getState(lids);
    if (state == ILidCommitState::State::NEED_COMMIT) {
        internalCommit(false);
    }
    if (state != ILidCommitState::State::COMPLETED) {
        unCommittedLidTracker.waitComplete(lids);
    }
}

bool
VisibilityHandler::startCommit(const std::lock_guard<std::mutex> &unused, bool force)
{
    (void) unused;
    SerialNum current = _serial.getSerialNum();
    if ((current > _lastCommitSerialNum) || force) {
        _writeService.master().execute(makeLambdaTask([this, force]() { performCommit(force);}));
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
        if (feedView) {
            feedView->forceCommit(current);
            _lastCommitSerialNum = current;
        }
    }
}

}
