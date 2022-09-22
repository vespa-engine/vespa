// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slime_filler_filter.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

namespace search::docsummary {

SlimeFillerFilter::Iterator::Iterator(bool should_render_in) noexcept
    : _should_render(should_render_in),
      _next()
{
}

SlimeFillerFilter::Iterator::Iterator(const SlimeFillerFilter* next) noexcept
    : _should_render(true),
      _next(next)
{
}

SlimeFillerFilter::Iterator
SlimeFillerFilter::Iterator::check_field(vespalib::stringref field_name) const
{
    assert(_should_render);
    return (_next != nullptr) ? _next->check_field(field_name) : SlimeFillerFilter::Iterator(true);
}

SlimeFillerFilter::SlimeFillerFilter()
    : _filter()
{
}

SlimeFillerFilter::~SlimeFillerFilter() = default;

SlimeFillerFilter::Iterator
SlimeFillerFilter::check_field(vespalib::stringref field_name) const
{
    auto itr = _filter.find(field_name);
    if (itr == _filter.end()) {
        // This field does not pass the filter -> should NOT be rendered.
        return SlimeFillerFilter::Iterator(false);
    }
    // This field passes the filter -> should be rendered.
    // We also keep track of the next filter in the hierarchy.
    return SlimeFillerFilter::Iterator(itr->second.get());
}

SlimeFillerFilter::Iterator
SlimeFillerFilter::begin() const
{
    return SlimeFillerFilter::Iterator(this);
}

bool
SlimeFillerFilter::empty() const { return _filter.empty(); }

SlimeFillerFilter&
SlimeFillerFilter::add(vespalib::stringref field_path)
{
    vespalib::stringref field_name;
    vespalib::stringref remaining_path;
    auto dot_pos = field_path.find('.');
    if (dot_pos != vespalib::string::npos) {
        field_name = field_path.substr(0, dot_pos);
        remaining_path = field_path.substr(dot_pos + 1);
    } else {
        field_name = field_path;
    }
    auto itr = _filter.find(field_name);
    if (itr != _filter.end()) {
        if (itr->second) {
            if (remaining_path.empty()) {
                itr->second.reset();
            } else {
                itr->second->add(remaining_path);
            }
        }
    } else {
        auto insres = _filter.insert(std::make_pair(field_name, std::unique_ptr<SlimeFillerFilter>()));
        assert(insres.second);
        if (!remaining_path.empty()) {
            insres.first->second = std::make_unique<SlimeFillerFilter>();
            insres.first->second->add(remaining_path);
        }
    }
    return *this;
}

void
SlimeFillerFilter::add_remaining(std::unique_ptr<SlimeFillerFilter>& filter, vespalib::stringref field_path)
{
    if (filter) {
        auto dot_pos = field_path.find('.');
        if (dot_pos != vespalib::string::npos) {
            auto remaining_path = field_path.substr(dot_pos + 1);
            if (!remaining_path.empty()) {
                filter->add(remaining_path);
            } else {
                filter.reset();
            }
        } else {
            filter.reset();
        }
    }
}

SlimeFillerFilter::Iterator
SlimeFillerFilter::all()
{
    return SlimeFillerFilter::Iterator(true);
}

}
