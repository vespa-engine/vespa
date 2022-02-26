// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "constant_value.h"

#include <map>
#include <memory>
#include <mutex>

namespace vespalib {
namespace eval {

/**
 * A cache enabling clients to share the constant values created by an
 * underlying factory. The returned wrappers are used to ensure
 * appropriate lifetime of created values. Used values are kept in the
 * cache and unused values are evicted from the cache.
 **/
class ConstantValueCache : public ConstantValueFactory
{
private:
    struct Cache {
        using SP = std::shared_ptr<Cache>;
        using Key = std::pair<vespalib::string, vespalib::string>;
        struct Value {
            size_t num_refs;
            ConstantValue::UP const_value;
            Value(ConstantValue::UP const_value_in)
                : num_refs(1), const_value(std::move(const_value_in)) {}
        };
        using Map = std::map<Key,Value>;
        std::mutex lock;
        Map cached;
    };

    struct Token : ConstantValue {
        Cache::SP cache;
        Cache::Map::iterator entry;
        Token(Cache::SP cache_in, Cache::Map::iterator entry_in)
            : cache(std::move(cache_in)), entry(entry_in) {}
        const ValueType &type() const override { return entry->second.const_value->type(); }
        const Value &value() const override { return entry->second.const_value->value(); }
        ~Token();
    };

    const ConstantValueFactory &_factory;
    Cache::SP _cache;

public:
    ConstantValueCache(const ConstantValueFactory &factory);
    ConstantValue::UP create(const vespalib::string &path, const vespalib::string &type) const override;
    ~ConstantValueCache() override;
};

} // namespace vespalib::eval
} // namespace vespalib
