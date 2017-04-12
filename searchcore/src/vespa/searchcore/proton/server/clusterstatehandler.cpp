// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.clusterstatehandler");
#include "clusterstatehandler.h"
#include "iclusterstatechangedhandler.h"
#include <vespa/vespalib/util/closuretask.h>
#include <algorithm>

using storage::spi::Bucket;
using storage::spi::BucketIdListResult;
using storage::spi::ClusterState;
using storage::spi::PartitionId;
using storage::spi::Result;
using vespalib::Executor;
using vespalib::makeTask;
using vespalib::makeClosure;

namespace proton {

namespace {

class ClusterStateAdapter : public IBucketStateCalculator
{
private:
    ClusterState _calc;

public:
    ClusterStateAdapter(const ClusterState &calc)
        : _calc(calc)
    {
    }

    virtual bool
    shouldBeReady(const document::BucketId &bucket) const override
    {
        return _calc.shouldBeReady(Bucket(bucket, PartitionId(0)));
    }

    virtual bool
    clusterUp(void) const override
    {
        return _calc.clusterUp();
    }

    virtual bool
    nodeUp(void) const override
    {
        return _calc.nodeUp();
    }

    virtual bool
    nodeInitializing() const override
    {
        return _calc.nodeInitializing();
    }
};

}


void
ClusterStateHandler::performSetClusterState(const ClusterState *calc,
        IGenericResultHandler *resultHandler)
{
    LOG(debug,
        "performSetClusterState(): "
        "clusterUp(%s), nodeUp(%s), nodeInitializing(%s)"
        "changedHandlers.size() = %zu",
        (calc->clusterUp() ? "true" : "false"),
        (calc->nodeUp() ? "true" : "false"),
        (calc->nodeInitializing() ? "true" : "false"),
        _changedHandlers.size());
    if (!_changedHandlers.empty()) {
        IBucketStateCalculator::SP newCalc(new ClusterStateAdapter(*calc));
        typedef std::vector<IClusterStateChangedHandler *> Chv;
        Chv &chs(_changedHandlers);
        for (Chv::const_iterator it = chs.begin(), ite = chs.end(); it != ite;
             ++it) {
            (*it)->notifyClusterStateChanged(newCalc);
        }
    }
    resultHandler->handle(Result());
}


void
ClusterStateHandler::performGetModifiedBuckets(
        IBucketIdListResultHandler *resultHandler)
{
    storage::spi::BucketIdListResult::List modifiedBuckets;
    modifiedBuckets.resize(_modifiedBuckets.size());
    std::copy(_modifiedBuckets.begin(), _modifiedBuckets.end(),
              modifiedBuckets.begin());

    if (LOG_WOULD_LOG(debug) && !modifiedBuckets.empty()) {
        std::ostringstream oss;
        for (size_t i = 0; i < modifiedBuckets.size(); ++i) {
            if (i != 0) {
                oss << ",";
            }
            oss << modifiedBuckets[i];
        }
        LOG(debug, "performGetModifiedBuckets(): modifiedBuckets(%zu): %s",
            modifiedBuckets.size(), oss.str().c_str());
    }
    resultHandler->handle(BucketIdListResult(modifiedBuckets));
    _modifiedBuckets.clear();
}


void
ClusterStateHandler::notifyBucketModified(const document::BucketId &bucket)
{
    _modifiedBuckets.insert(bucket);
}


ClusterStateHandler::ClusterStateHandler(Executor &executor)
    : IBucketModifiedHandler(),
      IClusterStateChangedNotifier(),
      _executor(executor),
      _changedHandlers(),
      _modifiedBuckets()
{
}


ClusterStateHandler::~ClusterStateHandler()
{
    assert(_changedHandlers.empty());
}


void
ClusterStateHandler::
addClusterStateChangedHandler(IClusterStateChangedHandler *handler)
{
    _changedHandlers.push_back(handler);
}


void
ClusterStateHandler::
removeClusterStateChangedHandler(IClusterStateChangedHandler *handler)
{
    auto it = std::find(_changedHandlers.begin(), _changedHandlers.end(),
                        handler);
    if (it != _changedHandlers.end()) {
        _changedHandlers.erase(it);
    }
}


void
ClusterStateHandler::handleSetClusterState(const ClusterState &calc,
        IGenericResultHandler &resultHandler)
{
    _executor.execute(makeTask(makeClosure(this,
                                       &proton::ClusterStateHandler::
                                       performSetClusterState,
                                       &calc,
                                       &resultHandler)));
}


void
ClusterStateHandler::handleGetModifiedBuckets(
        IBucketIdListResultHandler &resultHandler)
{
    _executor.execute(makeTask(makeClosure(this,
                                       &proton::ClusterStateHandler::
                                       performGetModifiedBuckets,
                                       &resultHandler)));
}


} // namespace proton
