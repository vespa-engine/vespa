// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/iclusterstatechangednotifier.h>
#include <vespa/searchcore/proton/server/iclusterstatechangedhandler.h>
#include <set>

namespace proton
{

namespace test
{

/**
 * Test cluster state handler that forwards cluster state change
 * notifications as appropriate.
 */
class ClusterStateHandler : public IClusterStateChangedNotifier
{
    std::set<IClusterStateChangedHandler *> _handlers;
public:
    ClusterStateHandler();

    virtual
    ~ClusterStateHandler();

    virtual void
    addClusterStateChangedHandler(IClusterStateChangedHandler *handler) override;

    virtual void
    removeClusterStateChangedHandler(IClusterStateChangedHandler *handler) override;

    void
    notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc);
};


} // namespace test

} // namespace proton
