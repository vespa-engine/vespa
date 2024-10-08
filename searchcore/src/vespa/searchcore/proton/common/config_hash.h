// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <string>
#include <vector>

namespace proton {

/*
 * Utility class to access a config vector based on name instead of index.
 * The vector must remain valid during the lifetime of the config hash.
 */
template <class Elem>
class ConfigHash {
    vespalib::hash_map<std::string, const Elem *> _hash;
public:
    ConfigHash(const std::vector<Elem> &config);
    ~ConfigHash();
    const Elem *lookup(const std::string &name) const;
};

} // namespace proton
