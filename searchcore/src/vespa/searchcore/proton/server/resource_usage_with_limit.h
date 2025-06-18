// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * Class representing a resource (e.g. disk or memory) with its current usage amd limit:
 *   - usage: How much of this resource is currently used (number between 0 and 1).
 *   - limit: How much of this resource is allowed to use (number between 0 and 1).
 *   - utilization: How much of the allowed part of this resource is used (usage/limit).
 */
class ResourceUsageWithLimit
{
private:
    double _usage;
    double _limit;

public:
    ResourceUsageWithLimit() noexcept
        : _usage(0),
          _limit(1.0)
    {
    }
    explicit ResourceUsageWithLimit(double usage_, double limit_) noexcept
        : _usage(usage_),
          _limit(limit_)
    {
    }
    bool operator==(const ResourceUsageWithLimit &rhs) const noexcept {
        return ((_usage == rhs._usage) &&
                (_limit == rhs._limit));
    }
    bool operator!=(const ResourceUsageWithLimit &rhs) const noexcept {
        return ! ((*this) == rhs);
    }
    double usage() const noexcept { return _usage; }
    double limit() const noexcept { return _limit; }
    double utilization() const noexcept { return _usage/_limit; }
    bool aboveLimit() const noexcept {
        return aboveLimit(1.0);
    }
    bool aboveLimit(double lowWatermarkFactor) const noexcept {
        return usage() > (limit() * lowWatermarkFactor);
    }
};

} // namespace proton
