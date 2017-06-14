// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * Class representing the state of an resource (e.g. disk or memory) with its limit and current usage.
 */
class ResourceUsageState
{
private:
    double _limit;
    double _usage;

public:
    ResourceUsageState()
        : _limit(0),
          _usage(0)
    {
    }
    ResourceUsageState(double limit_, double usage_)
        : _limit(limit_),
          _usage(usage_)
    {
    }
    bool operator==(const ResourceUsageState &rhs) const {
        return ((_limit == rhs._limit) &&
                (_usage == rhs._usage));
    }
    bool operator!=(const ResourceUsageState &rhs) const {
        return ! ((*this) == rhs);
    }
    double limit() const { return _limit; }
    double usage() const { return _usage; }
    bool aboveLimit() const {
        return aboveLimit(1.0);
    }
    bool aboveLimit(double lowWatermarkFactor) const {
        return usage() > (limit() * lowWatermarkFactor);
    }
};

} // namespace proton
