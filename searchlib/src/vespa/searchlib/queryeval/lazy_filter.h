// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <memory>
#include <vector>

namespace search::queryeval {

class LazyFilter : public std::enable_shared_from_this<LazyFilter> {
public:
    LazyFilter() noexcept = default;
    LazyFilter(const GlobalFilter &) = delete;
    LazyFilter(LazyFilter &&) = delete;
    virtual ~LazyFilter() {}
    virtual bool is_active() const = 0;
    virtual bool check(uint32_t docid) = 0;
    virtual std::shared_ptr<LazyFilter> clone() const = 0;
};

class InactiveLazyFilter : public LazyFilter {
private:
    struct Private { explicit Private() = default; };
public:
    InactiveLazyFilter(Private) noexcept {}
    static std::shared_ptr<InactiveLazyFilter> create() { return std::make_shared<InactiveLazyFilter>(Private()); }

    bool is_active() const override { return false; }
    bool check(uint32_t /*docid*/) override { return true; }
    std::shared_ptr<LazyFilter> clone() const override {
        return create();
    }
};

class GeoLocationLazyFilter : public LazyFilter {
private:
    struct Private { explicit Private() = default; };
    const common::Location &_location;
    uint32_t _num_values;
    std::vector<search::AttributeVector::largeint_t> _pos;

public:
    GeoLocationLazyFilter(Private, const common::Location &location) noexcept
        : _location(location),
          _num_values(0) {
    }
    static std::shared_ptr<GeoLocationLazyFilter> create(const common::Location &location) { return std::make_shared<GeoLocationLazyFilter>(Private(), location); }
    bool is_active() const override { return true; }
    bool check(uint32_t docid) override {
        _pos.resize(1);  // Needed (single-value attribute), cf. LocationIterator
        _num_values = _location.getVec()->get(docid, &_pos[0], _pos.size());
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
    std::shared_ptr<LazyFilter> clone() const override {
        return create(_location);
    }
};

} // namespace
