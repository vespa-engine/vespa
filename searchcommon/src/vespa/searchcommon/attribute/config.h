// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basictype.h"
#include "collectiontype.h"
#include "predicate_params.h"
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/searchcommon/common/compaction_strategy.h>
#include <vespa/eval/eval/value_type.h>

namespace search::attribute {

class Config
{
public:
    Config();
    Config(BasicType bt, CollectionType ct = CollectionType::SINGLE,
           bool fastSearch_ = false, bool huge_ = false);
    Config(const Config &);
    Config & operator = (const Config &);
    Config(Config &&) noexcept;
    Config & operator = (Config &&) noexcept;
    ~Config();

    BasicType basicType()                 const { return _basicType; }
    CollectionType collectionType()       const { return _type; }
    bool fastSearch()                     const { return _fastSearch; }
    bool huge()                           const { return _huge; }
    const PredicateParams &predicateParams() const { return _predicateParams; }
    vespalib::eval::ValueType tensorType() const { return _tensorType; }

    /**
     * Check if attribute posting list can consist of a bitvector in
     * addition to (or instead of) a btree. 
     */
    bool getEnableBitVectors() const { return _enableBitVectors; }

    /**
     * Check if attribute posting list can consist of only a bitvector with
     * no corresponding btree.
     */
    bool getEnableOnlyBitVector() const { return _enableOnlyBitVector; }

    bool getIsFilter() const { return _isFilter; }
    bool isMutable() const { return _mutable; }

    /**
     * Check if this attribute should be fast accessible at all times.
     * If so, attribute is kept in memory also for non-searchable documents.
     */
    bool fastAccess() const { return _fastAccess; }

    const GrowStrategy & getGrowStrategy() const { return _growStrategy; }
    const CompactionStrategy &getCompactionStrategy() const { return _compactionStrategy; }
    Config & setHuge(bool v)                         { _huge = v; return *this;}
    Config & setFastSearch(bool v)                   { _fastSearch = v; return *this; }
    Config & setPredicateParams(const PredicateParams &v) { _predicateParams = v; return *this; }
    Config & setTensorType(const vespalib::eval::ValueType &tensorType_in) {
        _tensorType = tensorType_in;
        return *this;
    }

    /**
     * Enable attribute posting list to consist of a bitvector in
     * addition to (or instead of) a btree. 
     */
    Config & setEnableBitVectors(bool enableBitVectors) {
        _enableBitVectors = enableBitVectors;
        return *this;
    }

    /**
     * Enable attribute posting list to consist of only a bitvector with
     * no corresponding btree. Some information degradation might occur when
     * document frequency goes down, since recreated btree representation
     * will then have lost weight information.
     */
    Config & setEnableOnlyBitVector(bool enableOnlyBitVector) {
        _enableOnlyBitVector = enableOnlyBitVector;
        return *this;
    }

    /**
     * Hide weight information when searching in attributes.
     */
    Config & setIsFilter(bool isFilter) { _isFilter = isFilter; return *this; }

    Config & setMutable(bool isMutable) { _mutable = isMutable; return *this; }
    Config & setFastAccess(bool v) { _fastAccess = v; return *this; }
    Config & setGrowStrategy(const GrowStrategy &gs) { _growStrategy = gs; return *this; }
    Config &setCompactionStrategy(const CompactionStrategy &compactionStrategy) { _compactionStrategy = compactionStrategy; return *this; }
    bool operator!=(const Config &b) const { return !(operator==(b)); }
    bool operator==(const Config &b) const;

private:
    BasicType      _basicType;
    CollectionType _type;
    bool           _fastSearch;
    bool           _huge;
    bool           _enableBitVectors;
    bool           _enableOnlyBitVector;
    bool           _isFilter;
    bool           _fastAccess;
    bool           _mutable;
    GrowStrategy   _growStrategy;
    CompactionStrategy _compactionStrategy;
    PredicateParams    _predicateParams;
    vespalib::eval::ValueType _tensorType;
};

}
