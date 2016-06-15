// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>

#include "clusterstatehandler.h"


namespace proton
{

namespace test
{


ClusterStateHandler::ClusterStateHandler()
    : IClusterStateChangedNotifier(),
      _handlers()
{
}


ClusterStateHandler::~ClusterStateHandler()
{
    assert(_handlers.empty());
}


void
ClusterStateHandler::
addClusterStateChangedHandler(IClusterStateChangedHandler *handler)
{
    _handlers.insert(handler);
}


void
ClusterStateHandler::
removeClusterStateChangedHandler(IClusterStateChangedHandler *handler)
{
    _handlers.erase(handler);
}


void
ClusterStateHandler::
notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc)
{
    for (auto &handler : _handlers) {
        handler->notifyClusterStateChanged(newCalc);
    }
}


} // namespace test

} // namespace proton

