// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "current_index_setup.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

namespace search::expression {

void
CurrentIndexSetup::Usage::notify_unbound_struct_usage(vespalib::stringref name)
{
    _unbound.insert(name);
}

CurrentIndexSetup::Usage::Usage()
  : _unbound()
{
}

CurrentIndexSetup::Usage::~Usage() = default;

vespalib::stringref
CurrentIndexSetup::Usage::get_unbound_struct_name() const
{
    assert(has_single_unbound_struct());
    return *_unbound.begin();
}

CurrentIndexSetup::Usage::Bind::Bind(CurrentIndexSetup &setup, Usage &usage) noexcept
  : _setup(setup)
{
    auto prev = setup.capture(std::addressof(usage));
    assert(prev == nullptr); // no nesting
}

CurrentIndexSetup::Usage::Bind::~Bind()
{
    [[maybe_unused]] auto prev = _setup.capture(nullptr);
}

CurrentIndexSetup::CurrentIndexSetup()
  : _bound(), _usage(nullptr)
{
}

CurrentIndexSetup::~CurrentIndexSetup() = default;

const CurrentIndex *
CurrentIndexSetup::resolve(vespalib::stringref field_name) const
{
    size_t pos = field_name.rfind('.');
    if (pos > field_name.size()) {
        return nullptr;
    }
    auto struct_name = field_name.substr(0, pos);
    auto entry = _bound.find(struct_name);
    if (entry == _bound.end()) {
        if (_usage != nullptr) {
            _usage->notify_unbound_struct_usage(struct_name);
        }
        return nullptr;
    }
    return entry->second;
}

void
CurrentIndexSetup::bind(vespalib::stringref struct_name, const CurrentIndex &index)
{
    auto res = _bound.insert(std::make_pair(vespalib::string(struct_name),
                                            std::addressof(index)));
    assert(res.second); // struct must be either bound or unbound
}

}
