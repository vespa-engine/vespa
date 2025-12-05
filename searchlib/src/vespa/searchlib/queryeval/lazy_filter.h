// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "global_filter.h"
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <memory>
#include <vector>

namespace search::queryeval {

class GeoLocationLazyFilter : public GlobalFilter {
private:
    struct Private { explicit Private() = default; };
    const common::Location &_location;
    uint32_t _docid_limit;
    Blueprint::HitEstimate _estimate;
    mutable std::vector<search::AttributeVector::largeint_t> _pos;

public:
    GeoLocationLazyFilter(Private, const common::Location &location, const Blueprint::HitEstimate &estimate) noexcept
        : _location(location),
          _docid_limit(_location.getVec()->getCommittedDocIdLimit()),
          _estimate(estimate) {
          _pos.resize(1);  // Needed (single-value attribute), cf. LocationIterator
    }
    static std::shared_ptr<GeoLocationLazyFilter> create(const common::Location &location, const Blueprint::HitEstimate &estimate) { return std::make_shared<GeoLocationLazyFilter>(Private(), location, estimate); }
    bool is_active() const override { return true; }
    uint32_t size() const override { return _docid_limit; }
    uint32_t count() const override { return _estimate.empty ? _docid_limit : std::min(_docid_limit, _estimate.estHits); }
    bool check(uint32_t docid) const override {
        if (docid >= _docid_limit) {
            return false;
        }
        uint32_t _num_values = _location.getVec()->get(docid, &_pos[0], _pos.size());
        while (_num_values > _pos.size()) {
            _pos.resize(_num_values);
            _num_values = _location.getVec()->get(docid, &_pos[0], _pos.size());
        }

        for (uint32_t i = 0; i < _num_values; i++) {
            int64_t docxy(_pos[i]);
            if (_location.inside_limit(docxy)) {
                return true;
            }
        }

        return false;
    }
};

class FallbackFilter : public GlobalFilter {
private:
    struct Private { explicit Private() = default; };
    const GlobalFilter &_global_filter;
    const GlobalFilter &_fallback;
public:
    FallbackFilter(Private, const GlobalFilter &global_filter, const GlobalFilter &fallback)
        : _global_filter(global_filter), _fallback(fallback) {
        assert(_global_filter.is_active());
        assert(_fallback.is_active());
    }
    static std::shared_ptr<FallbackFilter> create(const GlobalFilter &global_filter, const GlobalFilter &fallback) { return std::make_shared<FallbackFilter>(Private(), global_filter, fallback); }
    bool is_active() const override {
        return true;
    }
    uint32_t size() const override {
        return std::min(_global_filter.size(), _fallback.size());
    }
    uint32_t count() const override {
        return std::min(_global_filter.count(), _fallback.count());
    }
    bool check(uint32_t docid) const override {
        return _global_filter.check(docid) && _fallback.check(docid);
    }
};

} // namespace
