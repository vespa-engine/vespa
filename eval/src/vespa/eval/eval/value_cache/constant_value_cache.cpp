// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "constant_value_cache.h"
#include <cassert>

namespace vespalib {
namespace eval {

ConstantValueCache::Token::~Token()
{
    std::lock_guard<std::mutex> guard(cache->lock);
    if (--(entry->second.num_refs) == 0) {
        cache->cached.erase(entry);
    }
}

ConstantValueCache::ConstantValueCache(const ConstantValueFactory &factory)
    : _factory(factory),
      _cache(std::make_shared<Cache>())
{
}

ConstantValueCache::~ConstantValueCache() = default;

ConstantValue::UP
ConstantValueCache::create(const vespalib::string &path, const vespalib::string &type) const
{
    Cache::Key key = std::make_pair(path, type);
    std::lock_guard<std::mutex> guard(_cache->lock);
    auto pos = _cache->cached.find(key);
    if (pos != _cache->cached.end()) {
        ++(pos->second.num_refs);
        return std::make_unique<Token>(_cache, pos);
    } else {
        auto res = _cache->cached.emplace(std::move(key), _factory.create(path, type));
        assert(res.second);
        return std::make_unique<Token>(_cache, res.first);
    }
}

} // namespace vespalib::eval
} // namespace vespalib
