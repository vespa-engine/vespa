// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_status.h"
#include "delegatedstatusrequest.h"

namespace storage::distributor {

std::ostream& DistributorStatus::getStream() {
    return _request.outputStream;
}
const framework::HttpUrlPath& DistributorStatus::getPath() const {
    return _request.path;
}
const framework::StatusReporter& DistributorStatus::getReporter() const {
    return _request.reporter;
}

void DistributorStatus::notifyCompleted() {
    {
        std::lock_guard guard(_lock);
        _done = true;
    }
    _cond.notify_all();
}
void DistributorStatus::waitForCompletion() {
    std::unique_lock guard(_lock);
    while (!_done) {
        _cond.wait(guard);
    }
}

}
