// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "global_filter.h"
#include <vespa/searchlib/attribute/attributevector.h>
#include <memory>
#include <vector>

namespace search::common { class Location; }

namespace search::queryeval {

/**
 * Class for checking whether document ids match a Location.
 *
 * Performs the check by accessing the contents of the attribute vector of the given Location. Hence, it is not as fast
 * as other implementations of GlobalFilter.
 *
 * Not thread-safe. If an object of this class is to be used in multiple threads,
 * then a copy has to be created for every thread.
 **/
class LocationLazyFilter : public GlobalFilter {
private:
    struct Private { explicit Private() = default; };
    const common::Location &_location;
    uint32_t _docid_limit;
    Blueprint::HitEstimate _estimate;
    mutable std::vector<search::AttributeVector::largeint_t> _pos;

public:
    LocationLazyFilter(Private, const common::Location &location, const Blueprint::HitEstimate &estimate) noexcept;
    static std::shared_ptr<LocationLazyFilter> create(const common::Location &location, const Blueprint::HitEstimate &estimate);
    bool is_active() const override;
    uint32_t size() const override;
    uint32_t count() const override;
    bool check(uint32_t docid) const override;
};

/**
 * Class that combines two GlobalFilter objects into a single GlobalFilter.
 *
 * Corresponds to a logical 'and' of the two GlobalFilters. Evaluates the first filter first, and only if
 * the document passes that filter, it evaluates the second filter. Intended to be used to combine a cheap (the global filter)
 * and an expensive (the lazy filter) GlobalFilter.
 **/
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
