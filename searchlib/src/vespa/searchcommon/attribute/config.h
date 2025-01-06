// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basictype.h"
#include "collectiontype.h"
#include "hnsw_index_params.h"
#include "predicate_params.h"
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/searchcommon/common/dictionary_config.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <optional>

namespace search::attribute {

/**
 * Configuration for an attribute vector.
 *
 * Used to determine which implementation to instantiate.
 */
class Config {
public:
    enum class Match : uint8_t { CASED, UNCASED };
    using CompactionStrategy = vespalib::datastore::CompactionStrategy;
    Config() noexcept;
    Config(BasicType bt) noexcept : Config(bt, CollectionType::SINGLE) { }
    Config(BasicType bt, CollectionType ct) noexcept : Config(bt, ct, false) { }
    Config(BasicType bt, CollectionType ct, bool fastSearch_) noexcept;
    Config(const Config &);
    Config & operator = (const Config &);
    Config(Config &&) noexcept;
    Config & operator = (Config &&) noexcept;
    ~Config();

    BasicType basicType()                 const noexcept { return _basicType; }
    CollectionType collectionType()       const noexcept { return _type; }
    bool fastSearch()                     const noexcept { return _fastSearch; }
    bool paged()                          const noexcept { return _paged; }
    const PredicateParams &predicateParams() const noexcept { return _predicateParams; }
    const vespalib::eval::ValueType & tensorType() const noexcept { return _tensorType; }
    DistanceMetric distance_metric() const noexcept { return _distance_metric; }
    const std::optional<HnswIndexParams>& hnsw_index_params() const { return _hnsw_index_params; }

    /**
     * Check if attribute posting list can consist of only a bitvector with
     * no corresponding btree.
     */
    bool getIsFilter() const noexcept { return _isFilter; }
    bool isMutable() const noexcept { return _mutable; }

    /**
     * Check if this attribute should be fast accessible at all times.
     * If so, attribute is kept in memory also for non-searchable documents.
     */
    bool fastAccess() const noexcept { return _fastAccess; }

    const GrowStrategy & getGrowStrategy() const { return _growStrategy; }
    const CompactionStrategy &getCompactionStrategy() const { return _compactionStrategy; }
    const DictionaryConfig & get_dictionary_config() const { return _dictionary; }
    Match get_match() const { return _match; }
    Config & setFastSearch(bool v)                   { _fastSearch = v; return *this; }
    Config & setPredicateParams(const PredicateParams &v) { _predicateParams = v; return *this; }
    Config & setTensorType(const vespalib::eval::ValueType &tensorType_in) {
        _tensorType = tensorType_in;
        return *this;
    }
    Config& set_distance_metric(DistanceMetric value) {
        _distance_metric = value;
        return *this;
    }
    Config& set_hnsw_index_params(const HnswIndexParams& params);
    Config& clear_hnsw_index_params() {
        _hnsw_index_params.reset();
        return *this;
    }

    /**
     * Enable attribute posting list to consist of only a bitvector with
     * no corresponding btree. Some information degradation might occur when
     * document frequency goes down, since recreated btree representation
     * will then have lost weight information.
     */
    Config & setIsFilter(bool isFilter) { _isFilter = isFilter; return *this; }
    Config & setMutable(bool isMutable) { _mutable = isMutable; return *this; }
    Config & setPaged(bool paged_in) { _paged = paged_in; return *this; }
    Config & setFastAccess(bool v) { _fastAccess = v; return *this; }
    Config & setGrowStrategy(const GrowStrategy &gs) { _growStrategy = gs; return *this; }
    Config & setCompactionStrategy(const CompactionStrategy &compactionStrategy) {
        _compactionStrategy = compactionStrategy;
        return *this;
    }
    Config & set_dictionary_config(const DictionaryConfig & cfg) { _dictionary = cfg; return *this; }
    Config & set_match(Match match) { _match = match; return *this; }
    bool operator!=(const Config &b) const noexcept { return !(operator==(b)); }
    bool operator==(const Config &b) const noexcept ;

    uint64_t getMaxUnCommittedMemory() const noexcept { return _maxUnCommittedMemory; }
    Config & setMaxUnCommittedMemory(uint64_t value) { _maxUnCommittedMemory = value; return *this; }
    std::string type_to_string() const;

private:
    BasicType      _basicType;
    CollectionType _type;
    bool           _fastSearch : 1;
    bool           _isFilter : 1;
    bool           _fastAccess : 1;
    bool           _mutable : 1;
    bool           _paged : 1;
    DistanceMetric                 _distance_metric;
    Match                          _match;
    DictionaryConfig               _dictionary;
    uint64_t                       _maxUnCommittedMemory;
    GrowStrategy                   _growStrategy;
    CompactionStrategy             _compactionStrategy;
    PredicateParams                _predicateParams;
    vespalib::eval::ValueType      _tensorType;
    std::optional<HnswIndexParams> _hnsw_index_params;
};

}
