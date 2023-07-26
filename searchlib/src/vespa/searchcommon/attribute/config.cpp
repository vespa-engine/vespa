// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config.h"

namespace search::attribute {

namespace {

static constexpr uint64_t MAX_UNCOMMITTED_MEMORY = 8000;

}

Config::Config() noexcept
    : Config(BasicType::NONE, CollectionType::SINGLE, false)
{
}

Config::Config(BasicType bt, CollectionType ct, bool fastSearch_) noexcept
    : _basicType(bt),
      _type(ct),
      _fastSearch(fastSearch_),
      _isFilter(false),
      _fastAccess(false),
      _mutable(false),
      _paged(false),
      _distance_metric(DistanceMetric::Euclidean),
      _match(Match::UNCASED),
      _dictionary(),
      _maxUnCommittedMemory(MAX_UNCOMMITTED_MEMORY),
      _growStrategy(),
      _compactionStrategy(),
      _predicateParams(),
      _tensorType(vespalib::eval::ValueType::error_type()),
      _hnsw_index_params()
{
}

Config::Config(const Config &) = default;
Config & Config::operator = (const Config &) = default;
Config::Config(Config &&) noexcept = default;
Config & Config::operator = (Config &&) noexcept = default;
Config::~Config() = default;

bool
Config::operator==(const Config &b) const noexcept
{
    return _basicType == b._basicType &&
           _type == b._type &&
           _fastSearch == b._fastSearch &&
           _isFilter == b._isFilter &&
           _fastAccess == b._fastAccess &&
           _mutable == b._mutable &&
           _paged == b._paged &&
           _maxUnCommittedMemory == b._maxUnCommittedMemory &&
           _match == b._match &&
           _dictionary == b._dictionary &&
           _growStrategy == b._growStrategy &&
           _compactionStrategy == b._compactionStrategy &&
           _predicateParams == b._predicateParams &&
           (_basicType.type() != BasicType::Type::TENSOR ||
            _tensorType == b._tensorType) &&
            _distance_metric == b._distance_metric &&
            _hnsw_index_params == b._hnsw_index_params;
}

Config&
Config::set_hnsw_index_params(const HnswIndexParams& params) {
    assert(_distance_metric == params.distance_metric());
    _hnsw_index_params = params;
    return *this;
}

}
