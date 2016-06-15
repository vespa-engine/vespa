// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document
{

class BucketId;

}

namespace proton
{

struct IBucketStateCalculator
{
    typedef std::shared_ptr<IBucketStateCalculator> SP;
    virtual bool shouldBeReady(const document::BucketId &bucket) const = 0;

    virtual bool
    clusterUp(void) const = 0;

    virtual bool
    nodeUp(void) const = 0;

    virtual bool
    nodeInitializing() const = 0;

    virtual ~IBucketStateCalculator() {}
};


} // namespace proton

