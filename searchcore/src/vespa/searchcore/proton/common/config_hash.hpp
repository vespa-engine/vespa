// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "config_hash.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

namespace proton {

template <class Elem>
ConfigHash<Elem>::ConfigHash(const std::vector<Elem> &config)
    : _hash()
{
    for (const auto &elem : config) {
        auto insres = _hash.insert(std::make_pair(elem.name, &elem));
        assert(insres.second);
    }
}

template <class Elem>
ConfigHash<Elem>::~ConfigHash()
{
}

template <class Elem>
const Elem *
ConfigHash<Elem>::lookup(const vespalib::string &name) const
{
    auto itr = _hash.find(name);
    return ((itr == _hash.end()) ? nullptr : itr->second);
}

} // namespace proton
