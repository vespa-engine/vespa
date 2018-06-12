// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "visibilityhandler.h"
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
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
            performCommit(true);
        } else {
            std::lock_guard<std::mutex> guard(_lock);
            startCommit(guard, true);
        }
    }
}

void VisibilityHandler::commitAndWait()
{
    if (_visibilityDelay != 0) {
        if (_writeService.master().isCurrentThread()) {
            performCommit(false);
        } else {
            std::lock_guard<std::mutex> guard(_lock);
            if (startCommit(guard, false)) {
                _writeService.master().sync();
            }
        }
    }
    // Always sync attribute writer threads so attribute vectors are
    // properly updated when document retriver rebuilds document
    _writeService.attributeFieldWriter().sync();
    _writeService.summary().sync();
}

bool VisibilityHandler::startCommit(const std::lock_guard<std::mutex> &unused, bool force)
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

void VisibilityHandler::performCommit(bool force)
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
