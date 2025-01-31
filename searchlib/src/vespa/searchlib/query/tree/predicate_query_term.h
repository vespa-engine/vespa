// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>
#include <vector>

namespace search::query {

/**
 * Represents a predicate query, with features and range features.
 */
class PredicateQueryTerm {
    static const uint64_t ALL_SUB_QUERIES = 0xffffffffffffffffULL;

    template <typename ValueType>
    class Entry {
        std::string _key;
        ValueType _value;
        uint64_t _sub_query_bitmap;

    public:
        Entry(std::string key, ValueType value, uint64_t sub_query_bitmap = ALL_SUB_QUERIES) noexcept
            : _key(std::move(key)), _value(std::move(value)), _sub_query_bitmap(sub_query_bitmap) {}
        ~Entry();

        const std::string & getKey() const { return _key; }
        const ValueType & getValue() const { return _value; }
        uint64_t getSubQueryBitmap() const { return _sub_query_bitmap; }
        bool operator==(const Entry<ValueType> &other) const {
            return _key == other._key
                && _value == other._value
                && _sub_query_bitmap == other._sub_query_bitmap;
        }
    };

    std::vector<Entry<std::string>> _features;
    std::vector<Entry<uint64_t>> _range_features;

public:
    using UP = std::unique_ptr<PredicateQueryTerm>;

    PredicateQueryTerm() noexcept : _features(), _range_features() {}

    void addFeature(std::string key, std::string value, uint64_t sub_query_bitmask = ALL_SUB_QUERIES) {
        _features.emplace_back(std::move(key), std::move(value), sub_query_bitmask);
    }

    void addRangeFeature(std::string key, uint64_t value, uint64_t sub_query_bitmask = ALL_SUB_QUERIES) {
        _range_features.emplace_back(std::move(key), value, sub_query_bitmask);
    }

    const std::vector<Entry<std::string>> &getFeatures() const
    { return _features; }
    const std::vector<Entry<uint64_t>> &getRangeFeatures() const
    { return _range_features; }

    bool operator==(const PredicateQueryTerm &other) const {
        return _features == other._features
            && _range_features == other._range_features;
    }
};

template <typename ValueType>
PredicateQueryTerm::Entry<ValueType>::~Entry() = default;

}
