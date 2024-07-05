// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <vector>

namespace search::query {

/**
 * Represents a predicate query, with features and range features.
 */
class PredicateQueryTerm {
    static const uint64_t ALL_SUB_QUERIES = 0xffffffffffffffffULL;

    template <typename ValueType>
    class Entry {
        vespalib::string _key;
        ValueType _value;
        uint64_t _sub_query_bitmap;

    public:
        Entry(std::string_view key, const ValueType &value,
              uint64_t sub_query_bitmap = ALL_SUB_QUERIES) noexcept
            : _key(key), _value(value), _sub_query_bitmap(sub_query_bitmap) {}

        const vespalib::string & getKey() const { return _key; }
        const ValueType & getValue() const { return _value; }
        uint64_t getSubQueryBitmap() const { return _sub_query_bitmap; }
        bool operator==(const Entry<ValueType> &other) const {
            return _key == other._key
                && _value == other._value
                && _sub_query_bitmap == other._sub_query_bitmap;
        }
    };

    std::vector<Entry<vespalib::string>> _features;
    std::vector<Entry<uint64_t>> _range_features;

public:
    using UP = std::unique_ptr<PredicateQueryTerm>;

    PredicateQueryTerm() noexcept : _features(), _range_features() {}

    void addFeature(std::string_view key, std::string_view value,
                    uint64_t sub_query_bitmask = ALL_SUB_QUERIES) {
        _features.emplace_back(key, value, sub_query_bitmask);
    }

    void addRangeFeature(std::string_view key, uint64_t value,
                         uint64_t sub_query_bitmask = ALL_SUB_QUERIES) {
        _range_features.emplace_back(key, value, sub_query_bitmask);
    }

    const std::vector<Entry<vespalib::string>> &getFeatures() const
    { return _features; }
    const std::vector<Entry<uint64_t>> &getRangeFeatures() const
    { return _range_features; }

    bool operator==(const PredicateQueryTerm &other) const {
        return _features == other._features
            && _range_features == other._range_features;
    }
};

}
