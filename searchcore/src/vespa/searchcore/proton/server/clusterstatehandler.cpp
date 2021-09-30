// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clusterstatehandler.h"
#include "iclusterstatechangedhandler.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.clusterstatehandler");

using storage::spi::Bucket;
using storage::spi::BucketIdListResult;
using storage::spi::ClusterState;
using storage::spi::Result;
using vespalib::Executor;
using vespalib::makeLambdaTask;

namespace proton {

namespace {

class ClusterStateAdapter : public IBucketStateCalculator
{
private:
    ClusterState _calc;
    bool _clusterUp;
    bool _nodeUp;
    bool _nodeInitializing;
    bool _nodeRetired;

public:
    ClusterStateAdapter(const ClusterState &calc)
        : _calc(calc),
          _clusterUp(_calc.clusterUp()),
          _nodeUp(_calc.nodeUp()),
          _nodeInitializing(_calc.nodeInitializing()),
          _nodeRetired(_calc.nodeRetired())
    {
    }
    vespalib::Trinary shouldBeReady(const document::Bucket &bucket) const override {
        return _calc.shouldBeReady(Bucket(bucket));
    }
    bool clusterUp() const override { return _clusterUp; }
    bool nodeUp() const override { return _nodeUp; }
    bool nodeInitializing() const override { return _nodeInitializing; }
    bool nodeRetired() const override { return _nodeRetired; }
};

}

void
ClusterStateHandler::performSetClusterState(const ClusterState *calc, IGenericResultHandler *resultHandler)
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
        auto newCalc = std::make_shared<ClusterStateAdapter>(*calc);
        for (const auto & handler : _changedHandlers ) {
            handler->notifyClusterStateChanged(newCalc);
        }
    }
    resultHandler->handle(Result());
}

void
ClusterStateHandler::performGetModifiedBuckets(IBucketIdListResultHandler *resultHandler)
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
    auto it = std::find(_changedHandlers.begin(), _changedHandlers.end(), handler);
    if (it != _changedHandlers.end()) {
        _changedHandlers.erase(it);
    }
}

void
ClusterStateHandler::handleSetClusterState(const ClusterState &calc, IGenericResultHandler &resultHandler)
{
    _executor.execute(makeLambdaTask([&]() {
        performSetClusterState(&calc, &resultHandler);
    }));
}

void
ClusterStateHandler::handleGetModifiedBuckets(IBucketIdListResultHandler &resultHandler)
{
    _executor.execute(makeLambdaTask([&]() {
        performGetModifiedBuckets(&resultHandler);
    }));
}

}
