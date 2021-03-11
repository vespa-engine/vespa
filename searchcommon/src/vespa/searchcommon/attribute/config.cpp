// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config.h"

namespace search::attribute {

Config::Config() noexcept :
    _basicType(BasicType::NONE),
    _type(CollectionType::SINGLE),
    _fastSearch(false),
    _huge(false),
    _enableBitVectors(false),
    _enableOnlyBitVector(false),
    _isFilter(false),
    _fastAccess(false),
    _mutable(false),
    _dictionary(),
    _growStrategy(),
    _compactionStrategy(),
    _predicateParams(),
    _tensorType(vespalib::eval::ValueType::error_type()),
    _distance_metric(DistanceMetric::Euclidean),
    _hnsw_index_params()
{
}

Config::Config(BasicType bt, CollectionType ct, bool fastSearch_, bool huge_) noexcept
    : _basicType(bt),
      _type(ct),
      _fastSearch(fastSearch_),
      _huge(huge_),
      _enableBitVectors(false),
      _enableOnlyBitVector(false),
      _isFilter(false),
      _fastAccess(false),
      _mutable(false),
      _dictionary(),
      _growStrategy(),
      _compactionStrategy(),
      _predicateParams(),
      _tensorType(vespalib::eval::ValueType::error_type()),
      _distance_metric(DistanceMetric::Euclidean),
      _hnsw_index_params()
{
}

Config::Config(const Config &) = default;
Config & Config::operator = (const Config &) = default;
Config::Config(Config &&) noexcept = default;
Config & Config::operator = (Config &&) noexcept = default;
Config::~Config() = default;

bool
Config::operator==(const Config &b) const
{
    return _basicType == b._basicType &&
           _type == b._type &&
           _huge == b._huge &&
           _fastSearch == b._fastSearch &&
           _enableBitVectors == b._enableBitVectors &&
           _enableOnlyBitVector == b._enableOnlyBitVector &&
           _isFilter == b._isFilter &&
           _fastAccess == b._fastAccess &&
           _mutable == b._mutable &&
           _dictionary == b._dictionary &&
           _growStrategy == b._growStrategy &&
           _compactionStrategy == b._compactionStrategy &&
           _predicateParams == b._predicateParams &&
           (_basicType.type() != BasicType::Type::TENSOR ||
            _tensorType == b._tensorType) &&
            _distance_metric == b._distance_metric &&
            _hnsw_index_params == b._hnsw_index_params;
}

}
