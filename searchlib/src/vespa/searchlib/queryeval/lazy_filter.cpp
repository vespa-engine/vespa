// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lazy_filter.h"
#include <vespa/searchlib/common/location.h>
#include <vespa/vespalib/util/require.h>

namespace search::queryeval {

LocationLazyFilter::LocationLazyFilter(Private, const common::Location &location, const Blueprint::HitEstimate &estimate) noexcept
    : _location(location),
      _docid_limit(_location.getVec()->getCommittedDocIdLimit()),
      _estimate(estimate)
{
    _pos.resize(1);  // Needed (single-value attribute), cf. LocationIterator
}

std::shared_ptr<LocationLazyFilter>
LocationLazyFilter::create(const common::Location &location, const Blueprint::HitEstimate &estimate)
{
    return std::make_shared<LocationLazyFilter>(Private(), location, estimate);
}

bool
LocationLazyFilter::is_active() const
{
    return true;
}

uint32_t
LocationLazyFilter::size() const
{
    return (_docid_limit == 0) ? 0 : _docid_limit - 1;
}

uint32_t
LocationLazyFilter::count() const
{
    return _estimate.empty ? 0 : std::min(size(), _estimate.estHits);
}

bool
LocationLazyFilter::check(uint32_t docid) const
{
    if (docid >= _docid_limit) {
        return false;
    }
    uint32_t num_values = _location.getVec()->get(docid, &_pos[0], _pos.size());
    while (num_values > _pos.size()) {
        _pos.resize(num_values);
        num_values = _location.getVec()->get(docid, &_pos[0], _pos.size());
    }

    for (uint32_t i = 0; i < num_values; i++) {
        int64_t docxy(_pos[i]);
        if (_location.inside_limit(docxy)) {
            return true;
        }
    }

    return false;
}

FallbackFilter::FallbackFilter(Private, const GlobalFilter &global_filter, const GlobalFilter &fallback)
        : _global_filter(global_filter), _fallback(fallback)
{
    REQUIRE(_global_filter.is_active());
    REQUIRE(_fallback.is_active());
}

std::shared_ptr<FallbackFilter>
FallbackFilter::create(const GlobalFilter &global_filter, const GlobalFilter &fallback)
{
    return std::make_shared<FallbackFilter>(Private(), global_filter, fallback);
}

bool
FallbackFilter::is_active() const
{
    return true;
}

uint32_t
FallbackFilter::size() const
{
    return std::min(_global_filter.size(), _fallback.size());
}

uint32_t
FallbackFilter::count() const
{
    return std::min(_global_filter.count(), _fallback.count());
}

bool
FallbackFilter::check(uint32_t docid) const
{
    return _global_filter.check(docid) && _fallback.check(docid);
}

AndFilter::AndFilter(Private, std::vector<std::shared_ptr<GlobalFilter>> &&children)
            : _children(std::move(children))
{
}

std::shared_ptr<AndFilter>
AndFilter::create(std::vector<std::shared_ptr<GlobalFilter>> &&children)
{
    return std::make_shared<AndFilter>(Private(), std::move(children));
}

bool
AndFilter::is_active() const
{
    for (auto &child : _children) {
        if (!child->is_active()) {
            return false;
        }
    }
    return true;
}

uint32_t
AndFilter::size() const
{
    uint32_t min = std::numeric_limits<uint32_t>::max();
    for (auto &child : _children) {
        min = std::min(min, child->size());
    }

    return min;
}

uint32_t
AndFilter::count() const
{
    uint32_t min = std::numeric_limits<uint32_t>::max();
    for (auto &child : _children) {
        min = std::min(min, child->count());
    }

    return min;
}

bool
AndFilter::check(uint32_t docid) const
{
    for (auto &child : _children) {
        if (!child->check(docid)) {
            return false;
        }

    }

    return true;
}

}
