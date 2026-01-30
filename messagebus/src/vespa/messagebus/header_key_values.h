// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <string>

namespace mbus {

class HeaderKeyValues {
public:
    // TODO custom immutable small map for KVs
    using Map = vespalib::hash_map<std::string, std::string>;
private:
    Map _map;
public:
    HeaderKeyValues();
    explicit HeaderKeyValues(Map&& map) noexcept;

    ~HeaderKeyValues();

    [[nodiscard]] bool empty() const noexcept { return _map.empty(); }
    [[nodiscard]] size_t size() const noexcept { return _map.size(); }
    [[nodiscard]] Map::const_iterator begin() const noexcept { return _map.begin(); }
    [[nodiscard]] Map::const_iterator end() const noexcept { return _map.end(); }
};

}
