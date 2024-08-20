// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <mutex>
#include <string>
#include <vector>

namespace vespalib::metrics {

// internal
class NameCollection {
private:
    using Map = std::map<std::string, size_t>;
    mutable std::mutex _lock;
    Map _names;
    std::vector<Map::const_iterator> _names_by_id;
public:
    const std::string &lookup(size_t id) const;
    size_t resolve(std::string_view name);
    size_t size() const;

    NameCollection();
    NameCollection(const NameCollection &) = delete;
    NameCollection & operator = (const NameCollection &) = delete;
    ~NameCollection();

    static constexpr size_t empty_id = 0;
};

}
