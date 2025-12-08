// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "global_filter.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <memory>
#include <vector>

namespace search::common { class Location; }

namespace search::queryeval {

class GeoLocationLazyFilter : public GlobalFilter {
private:
    struct Private { explicit Private() = default; };
    const common::Location &_location;
    uint32_t _docid_limit;
    Blueprint::HitEstimate _estimate;
    mutable std::vector<search::AttributeVector::largeint_t> _pos;

public:
    GeoLocationLazyFilter(Private, const common::Location &location, const Blueprint::HitEstimate &estimate) noexcept;
    static std::shared_ptr<GeoLocationLazyFilter> create(const common::Location &location, const Blueprint::HitEstimate &estimate);
    bool is_active() const override;
    uint32_t size() const override;
    uint32_t count() const override;
    bool check(uint32_t docid) const override;
};

class FallbackFilter : public GlobalFilter {
private:
    struct Private { explicit Private() = default; };
    const GlobalFilter &_global_filter;
    const GlobalFilter &_fallback;
public:
    FallbackFilter(Private, const GlobalFilter &global_filter, const GlobalFilter &fallback);
    static std::shared_ptr<FallbackFilter> create(const GlobalFilter &global_filter, const GlobalFilter &fallback);
    bool is_active() const override;
    uint32_t size() const override;
    uint32_t count() const override;
    bool check(uint32_t docid) const override;
};

} // namespace
