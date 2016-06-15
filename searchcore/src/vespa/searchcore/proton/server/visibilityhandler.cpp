// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visibilityhandler.h"
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
      _visibilityDelay(0),
      _lastCommitSerialNum(0),
      _lock()
{
}

void VisibilityHandler::commit()
{
    if (_visibilityDelay != 0) {
        if (_writeService.master().isCurrentThread()) {
            performCommit();
        } else {
            LockGuard guard(_lock);
            startCommit(guard);
        }
    }
}

void VisibilityHandler::commitAndWait()
{
    if (_visibilityDelay != 0) {
        if (_writeService.master().isCurrentThread()) {
            performCommit();
        } else {
            LockGuard guard(_lock);
            if (startCommit(guard)) {
                _writeService.master().sync();
            }
        }
    }
    // Always sync attribute writer threads so attribute vectors are
    // properly updated when document retriver rebuilds document
    _writeService.attributeFieldWriter().sync();
}

bool VisibilityHandler::startCommit(const LockGuard & unused)
{
    (void) unused;
    SerialNum current = _serial.getSerialNum();
    if (current > _lastCommitSerialNum) {
        _writeService.master().execute(makeTask(makeClosure(this,
             &VisibilityHandler::performCommit)));
        return true;
    }
    return false;
}

void VisibilityHandler::performCommit()
{
    // Called by master thread
    SerialNum current = _serial.getSerialNum();
    if (current > _lastCommitSerialNum) {
        IFeedView::SP feedView(_feedView.get());
        feedView->forceCommit(current);
        _lastCommitSerialNum = current;
    }
}

}
